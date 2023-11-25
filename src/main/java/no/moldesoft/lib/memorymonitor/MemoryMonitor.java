package no.moldesoft.lib.memorymonitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MemoryMonitor implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MemoryMonitor.class);
    private final Runtime runtime;
    private final String hostname;
    private final DateTimeFormatter dateTimeFormatter;
    private final Instant applicationStart;
    private final Mailer mailer;
    private final Locale locale;
    private final ZoneId zoneId;
    private volatile boolean firstRun;

    public MemoryMonitor(Instant applicationStart, Mailer mailer, Locale locale, ZoneId zoneId) {
        this.applicationStart = applicationStart;
        this.locale = locale == null ? Locale.forLanguageTag("no_NO") : locale;
        this.mailer = mailer;
        this.zoneId = zoneId == null ? ZoneId.systemDefault() : zoneId;
        this.runtime = Runtime.getRuntime();
        dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String hostname;
        try {
            InetAddress localhost = InetAddress.getLocalHost();
            hostname = localhost.getCanonicalHostName();
        } catch (UnknownHostException e) {
            LOGGER.warn(e.getMessage());
            hostname = "unknown";
        }
        this.hostname = hostname;
        firstRun = true;
    }

    @Override
    public void run() {
        try {
            executeAndSendMail();
            firstRun = false;
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private void executeAndSendMail() {
        String body = report();
        String title = "Memory snapshot [from " + hostname + ']';
        String from = "memorymonitor@kf.no";
        String to = "info@moldesoft.no";
        mailer.sendMail(from, to, title, body);
    }

    public String report() {
        return report(zoneId);
    }

    public String report(ZoneId zoneId) {
        Instant now = Instant.now();

        long freeMemory = runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();

        Instant jvmStart = Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime());

        String mbFree = mb(freeMemory, "Free  memory");
        String mbMax = mb(maxMemory, "Max   memory");
        String mbTotal = mb(totalMemory, "Total memory");

        List<String> aboutMemoryPools = aboutMemoryPools();

        List<String> report = new ArrayList<>();
        report.add("This report produced at:   " + ldt(now, zoneId).format(dateTimeFormatter));
        report.add("Application running since: " + ldt(applicationStart, zoneId).format(dateTimeFormatter));
        report.add("JVM started at:            " + ldt(jvmStart, zoneId).format(dateTimeFormatter));
        report.add(runningTime("Application running time:  ", applicationStart, now));
        report.add(runningTime("JVM running time:          ", jvmStart, now));
        if (firstRun) {
            report.add("");
            report.add("First run since application start");
        }
        report.add("");
        report.add(mbFree);
        report.add(mbMax);
        report.add(mbTotal);
        report.add("");
        report.addAll(aboutMemoryPools);

        return String.join("\r\n", report);
    }

    private LocalDateTime ldt(Instant instant, ZoneId zoneId) {
        return instant.atZone(zoneId).toLocalDateTime();
    }

    private List<String> aboutMemoryPools() {
        List<MemoryPoolMXBean> memoryPoolMXBeans = ManagementFactory.getMemoryPoolMXBeans();
        memoryPoolMXBeans.sort(Comparator.comparing(MemoryPoolMXBean::getType));
        List<String> lines = new ArrayList<>();
        lines.add(assemble("Memory Pool", "Type", "Initial", "Total", "Maximum", "Used", ""));
        memoryPoolMXBeans.stream().map(this::fmt).forEach(lines::add);
        return lines;
    }

    private String fmt(MemoryPoolMXBean memoryPoolMXBean) {
        String name = memoryPoolMXBean.getName();
        MemoryType type = memoryPoolMXBean.getType();
        MemoryUsage usage = memoryPoolMXBean.getUsage();
        long init = usage.getInit();
        long committed = usage.getCommitted();
        long max = usage.getMax();
        long used = usage.getUsed();
        String pct = max < 0L ? "" : String.format(locale, " (%3d%%)",
                Math.round(100d * (double) used / (double) max));
        return assemble(name, type.toString(), mb(init), mb(committed), mb(max), mb(used), pct);
    }

    private String assemble(String memoryPool, String type, String initial, String total, String maximum, String used, String pct) {
        return String.format("%22s  %16s  %14s  %14s  %14s  %14s  %6s",
                memoryPool, type, initial, total, maximum, used, pct);
    }

    private String mb(long freeMemory, String label) {
        double mb = freeMemory / (double) (1024L * 1024L);
        return String.format(locale, "%s: %,11.3f MB", label, mb);
    }

    private String mb(long amount) {
        return amount < 0
                ? String.format(locale, "%8s   ", "")
                : String.format(locale, "%,11.3f MB", amount / (1024d * 1024d));
    }

    private String runningTime(String label, Instant start, Instant now) {
        Duration duration = Duration.between(start, now);
        long seconds = duration.getSeconds();
        long minutes = TimeUnit.SECONDS.toMinutes(seconds);
        long secondsOfMinute = seconds - TimeUnit.MINUTES.toSeconds(minutes);

        long hours = TimeUnit.MINUTES.toHours(minutes);
        long minutesOfHour = minutes - TimeUnit.HOURS.toMinutes(hours);

        long days = TimeUnit.HOURS.toDays(hours);
        long hoursOfDay = hours - TimeUnit.DAYS.toHours(days);

        StringBuilder b = new StringBuilder(label);
        boolean includeZero = false;
        if (days > 0) {
            b.append(days).append(" day");
            if (days != 1L) {
                b.append('s');
            }
            b.append(' ');
            includeZero = true;
        }
        if (hoursOfDay > 0 || includeZero) {
            b.append(hoursOfDay).append(" hour");
            if (hoursOfDay != 1L) {
                b.append('s');
            }
            b.append(' ');
            includeZero = true;
        }
        if (minutesOfHour > 0 || includeZero) {
            b.append(minutesOfHour).append(" minute");
            if (minutesOfHour != 1L) {
                b.append('s');
            }
            b.append(' ');
        }
        b.append(secondsOfMinute).append(" second");
        if (secondsOfMinute != 1L) {
            b.append('s');
        }
        return b.toString();
    }

}
