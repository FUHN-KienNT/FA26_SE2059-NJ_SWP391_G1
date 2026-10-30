package vn.edu.fpt.ams.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import vn.edu.fpt.ams.dao.AssignmentOfferDAO;
import vn.edu.fpt.ams.dto.AssignmentOfferView;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.*;

/** Teacher consent and atomic additional class finalization for UC53. */
public class AssignmentOfferService {
    public enum Response { ACCEPTED, DECLINED, EXPIRED }
    private final Jdbc.Connections connections;
    private final AssignmentOfferDAO dao=new AssignmentOfferDAO();

    /** Use the configured AMS database. */
    public AssignmentOfferService() { this(DBConnection::getConnection); }
    /** Supply a database boundary for transaction and concurrency tests. */
    public AssignmentOfferService(Jdbc.Connections connections) { this.connections=connections; }

    /** Return only this active Teacher's offers, synchronizing elapsed deadlines first. */
    public List<AssignmentOfferView> list(long teacher) {
        return Jdbc.transaction(connections,c->{
            teacher(c,teacher);
            dao.expire(c,teacher,dao.now(c));
            return dao.list(c,teacher);
        });
    }

    /** Decide once under request/offer locks; expiry is committed rather than rolled back. */
    public Response respond(long teacher,long offerId,AssignmentOfferStatus decision,String note) {
        if(offerId<=0) throw new IllegalArgumentException("Mã lời mời không hợp lệ.");
        if(decision!=AssignmentOfferStatus.ACCEPTED&&decision!=AssignmentOfferStatus.DECLINED)
            throw new IllegalArgumentException("Chỉ có thể nhận hoặc từ chối lời mời.");
        String saved=note==null||note.isBlank()?null:note.strip();
        if(saved!=null&&saved.length()>500) throw new IllegalArgumentException("Ghi chú tối đa 500 ký tự.");
        return Jdbc.transaction(connections,c->{
            teacher(c,teacher);
            long requestId=dao.requestId(c,teacher,offerId);
            var request=dao.lockRequest(c,requestId);
            var offer=dao.lockOffer(c,teacher,offerId);
            var now=dao.now(c);
            if(offer.status()!=AssignmentOfferStatus.PENDING)
                throw new IllegalArgumentException("Lời mời đã được phản hồi, hết hạn hoặc hủy.");
            if(!now.isBefore(offer.expiresAt())) {
                dao.expire(c,teacher,now);
                return Response.EXPIRED;
            }
            if(now.isBefore(offer.sentAt())) throw new IllegalArgumentException("Lời mời chưa có hiệu lực.");
            if(!"APPROVED".equals(request.status())) throw new IllegalArgumentException("Yêu cầu không còn chờ phân công giảng viên.");
            if(decision==AssignmentOfferStatus.DECLINED) {
                dao.respond(c,offerId,teacher,decision,saved,now);
                return Response.DECLINED;
            }
            var source=dao.lockClass(c,request.sourceClassId());
            var offering=dao.offering(c,source.subjectTermId());
            if(!offering.active()) throw new IllegalArgumentException("Môn học hoặc học kỳ đã ngừng hoạt động.");
            if(!dao.eligibleTeacher(c,teacher,offering.subjectId()))
                throw new IllegalArgumentException("Bạn không còn đủ điều kiện giảng dạy môn học này.");
            long classId;
            if(request.createdClassId()==null) {
                var experts=dao.experts(c,source.id(),offering.subjectId());
                capacity(source.capacity());
                classId=dao.createClass(c,requestId,source,offering.subjectCode(),teacher,offering.subjectId(),experts);
            } else {
                var setup=dao.lockClass(c,request.createdClassId());
                if(setup.id()==source.id()||setup.subjectTermId()!=source.subjectTermId())
                    throw new IllegalArgumentException("Lớp bổ sung không thuộc đúng môn/học kỳ của yêu cầu.");
                capacity(setup.capacity());
                dao.experts(c,setup.id(),offering.subjectId());
                if(dao.occupiedSeats(c,setup.id(),dao.now(c))>setup.capacity())
                    throw new IllegalArgumentException("Sĩ số cấu hình thấp hơn số sinh viên và chỗ đang được giữ.");
                if(setup.teacherId()!=null) throw new IllegalArgumentException("Lớp bổ sung đã có giảng viên phụ trách.");
                dao.staffClass(c,setup.id(),teacher);
                classId=setup.id();
            }
            now=dao.now(c);
            // If processing crossed the deadline, roll back class creation and all other writes.
            if(!now.isBefore(offer.expiresAt())) throw new IllegalArgumentException("Lời mời vừa hết hạn. Vui lòng tải lại trang.");
            dao.respond(c,offerId,teacher,decision,saved,now);
            dao.resolve(c,requestId,classId,now);
            return Response.ACCEPTED;
        });
    }

    /** Periodic expiry leaves approved requests available for another staffing episode. */
    public int expireDue() {
        return Jdbc.transaction(connections,c->dao.expire(c,null,dao.now(c)));
    }

    /** Revalidate account status and role from the database for every operation. */
    private static void teacher(Connection c,long actor) throws SQLException {
        if(actor<=0||Access.active(c,actor)!=UserRole.TEACHER) throw new SecurityException("Cần tài khoản giảng viên đang hoạt động.");
    }

    /** Keep setup capacity within the business range even when records are stale. */
    private static void capacity(int value) {
        if(value<10||value>40) throw new IllegalArgumentException("Sĩ số lớp bổ sung phải từ 10 đến 40.");
    }

    /** Display offer states without requiring newer syntax in JSP source. */
    public static String statusLabel(AssignmentOfferStatus status) {
        return switch(status) {
            case PENDING -> "Chờ phản hồi"; case ACCEPTED -> "Đã nhận";
            case DECLINED -> "Đã từ chối"; case EXPIRED -> "Hết hạn"; case CANCELLED -> "Đã hủy";
        };
    }
}
