package vn.edu.fpt.ams.service;

import java.util.Locale;
import vn.edu.fpt.ams.dao.*;
import vn.edu.fpt.ams.dto.StudentSubjectDTO.*;
import vn.edu.fpt.ams.util.*;

/** Student subject discovery and atomic self-registration; no assignment or class-transfer writes. */
public class StudentSubjectService {
    private final Jdbc.Connections connections;
    private final StudentSubjectDAO dao=new StudentSubjectDAO();
    private final StudentDashboardDAO students=new StudentDashboardDAO();
    public StudentSubjectService(){this(DBConnection::getConnection);}
    public StudentSubjectService(Jdbc.Connections source){connections=source;}

    public java.util.List<vn.edu.fpt.ams.model.AcademicTerm> termOptions(long student) {
        return Jdbc.transaction(connections,c->{
            c.setReadOnly(true);requireStudent(c,student);
            return new MasterDataDAO().findTerms(c,null,null);
        });
    }
    public Page list(long student,String view,String search){
        String mode=view==null||view.isBlank()?"available":view;
        if(!mode.equals("available")&&!mode.equals("enrolled"))throw new IllegalArgumentException("Danh sách môn học không hợp lệ.");
        String query=search==null?"":search.strip();
        if(query.length()>100)throw new IllegalArgumentException("Từ khóa tìm kiếm không được quá 100 ký tự.");
        return Jdbc.transaction(connections,c->{
            c.setReadOnly(true);requireStudent(c,student);
            var rows=dao.list(c,student,mode.equals("enrolled"));
            String key=query.toLowerCase(Locale.ROOT);
            return new Page(rows.stream().filter(x->(x.subjectCode()+" "+x.subjectName()).toLowerCase(Locale.ROOT).contains(key)).toList(),mode,query);
        });
    }
    public Offering detail(long student,long offering){
        positive(offering);
        return Jdbc.transaction(connections,c->{c.setReadOnly(true);requireStudent(c,student);return dao.detail(c,student,offering);});
    }
    public long register(long student,long offering){
        positive(offering);
        return Jdbc.transaction(connections,c->{
            requireStudent(c,student);
            var context=dao.context(c,offering);dao.lockOffering(c,offering,context);
            var classes=dao.lockClasses(c,offering);
            var row=dao.detail(c,student,offering);
            if(!row.open())throw new IllegalArgumentException("Môn học không còn mở đăng ký trong học kỳ này.");
            if(row.enrolled())throw new IllegalArgumentException("Bạn đã đăng ký môn học này.");
            if(dao.hasPendingInvitation(c,student,offering))
                throw new IllegalArgumentException("Bạn đang có lời mời vào lớp của môn học này. Vui lòng sử dụng lời mời đã nhận.");
            for(long classId:classes)if(dao.seatAvailable(c,offering,classId)){
                dao.lockStudent(c,student);
                return dao.savePlacement(c,student,offering,classId);
            }
            throw new IllegalArgumentException("Chưa có lớp đủ điều kiện và còn chỗ. Vui lòng liên hệ giảng viên.");
        });
    }
    private void requireStudent(java.sql.Connection c,long student)throws java.sql.SQLException{
        if(student<=0||students.activeStudent(c,student).isEmpty())throw new SecurityException("Chỉ sinh viên đang hoạt động được sử dụng chức năng này.");
    }
    private static void positive(long id){if(id<=0)throw new IllegalArgumentException("Mã môn học trong học kỳ không hợp lệ.");}
}
