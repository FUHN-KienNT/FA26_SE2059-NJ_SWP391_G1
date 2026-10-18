package vn.edu.fpt.ams.listener;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import vn.edu.fpt.ams.service.StudentObjectiveService;

/** Deadline processing is server-side and survives closing a tab or restarting the application. */
@WebListener
public class AssignmentDeadlineListener implements ServletContextListener {
    private ScheduledExecutorService executor;

    @Override public void contextInitialized(ServletContextEvent event) {
        StudentObjectiveService service=new StudentObjectiveService();
        executor=Executors.newSingleThreadScheduledExecutor(task->{
            Thread thread=new Thread(task,"ams-assignment-deadlines");
            thread.setDaemon(true);return thread;
        });
        executor.scheduleWithFixedDelay(()->{
            try { service.finishOverdue(); }
            catch(RuntimeException ex) { event.getServletContext().log("Không tự động nộp được một số bài tập; sẽ thử lại.",ex); }
        },0,5,TimeUnit.SECONDS);
    }

    @Override public void contextDestroyed(ServletContextEvent event) {
        if(executor!=null) executor.shutdownNow();
    }
}
