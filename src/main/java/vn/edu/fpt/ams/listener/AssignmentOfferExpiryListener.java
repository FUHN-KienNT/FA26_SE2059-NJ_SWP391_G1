package vn.edu.fpt.ams.listener;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebListener;
import java.util.concurrent.*;
import vn.edu.fpt.ams.service.AssignmentOfferService;

/** Expiry continues when teachers leave the offer page or close their browsers. */
@WebListener
public class AssignmentOfferExpiryListener implements ServletContextListener {
    private ScheduledExecutorService executor;

    /** Start a daemon task; individual decisions still recheck the exact server deadline. */
    @Override public void contextInitialized(ServletContextEvent event) {
        executor=Executors.newSingleThreadScheduledExecutor(AssignmentOfferExpiryListener::expiryThread);
        executor.scheduleWithFixedDelay(()->expire(event),0,30,TimeUnit.SECONDS);
    }

    /** Build a named worker that does not keep a stopped Tomcat alive. */
    private static Thread expiryThread(Runnable task) {
        Thread thread=new Thread(task,"ams-assignment-offer-expiry"); thread.setDaemon(true); return thread;
    }

    /** Retry transient database failures on the next interval without killing the worker. */
    private static void expire(ServletContextEvent event) {
        try { new AssignmentOfferService().expireDue(); }
        catch(RuntimeException ex) { event.getServletContext().log("Chưa cập nhật được lời mời hết hạn; sẽ thử lại.",ex); }
    }

    /** Release the timer when the application is reloaded or stopped. */
    @Override public void contextDestroyed(ServletContextEvent event) {
        if(executor!=null) executor.shutdownNow();
    }
}
