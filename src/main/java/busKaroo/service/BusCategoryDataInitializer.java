package busKaroo.service;

import busKaroo.dao.BusCategoryRepository;
import busKaroo.dao.BusRepository;
import busKaroo.dao.RouteRepository;
import busKaroo.model.Bus;
import busKaroo.model.BusCategory;
import busKaroo.model.Route;
import busKaroo.model.enums.BusStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Adds standard categories and demo schedules, then spaces out future
 * duplicate departure times without changing trips that already have bookings.
 */
@Component
public class BusCategoryDataInitializer implements ApplicationRunner {

    private static final Map<String, String> DEFAULT_CATEGORIES = Map.of(
            "Normal", "Non-AC standard intercity bus with a 2+2 seating plan.",
            "Luxury", "Premium air-conditioned coach with a wider 2+1 seating plan.",
            "Sleeper", "Overnight bus with a 1+1 sleeper berth plan.");

    private final BusCategoryRepository categoryRepository;
    private final BusRepository busRepository;
    private final RouteRepository routeRepository;
    private final JdbcTemplate jdbcTemplate;

    // Dev environments with seeded databases should not generate or shift
    // thousands of demo trips on every Spring Boot restart.
    @Value("${buskaroo.initializer.seed-trips:false}")
    private boolean seedTrips;

    @Value("${buskaroo.initializer.adjust-departures:false}")
    private boolean adjustDepartures;

    // A safety cap for the legacy all-pairs timetable comparison.
    // Each trip with N stops generates N * (N - 1) keys.
    private static final long MAX_DEPARTURE_KEYS = 100_000L;

    public BusCategoryDataInitializer(BusCategoryRepository categoryRepository, BusRepository busRepository,
            RouteRepository routeRepository, JdbcTemplate jdbcTemplate) {
        this.categoryRepository = categoryRepository;
        this.busRepository = busRepository;
        this.routeRepository = routeRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        DEFAULT_CATEGORIES.forEach((name, description) -> {
            if (!categoryRepository.existsByNameIgnoreCase(name)) {
                BusCategory category = new BusCategory();
                category.setName(name);
                category.setDescription(description);
                categoryRepository.save(category);
            }
        });

        // The existing-db profile does not rerun data.sql. Normalize only the
        // numbered demo fleet so its current trips also expose all three plans.
        Map<String, BusCategory> standardCategories = new HashMap<>();
        categoryRepository.findAll().forEach(category -> {
            if (DEFAULT_CATEGORIES.containsKey(category.getName()))
                standardCategories.put(category.getName(), category);
        });
        ArrayList<Bus> changedBuses = new ArrayList<>();
        for (Bus bus : busRepository.findAll()) {
            String registration = bus.getRegistrationNumber();
            if (registration == null || !registration.matches(".*-AB-(10[0-9]{2}|1100)$"))
                continue;
            String currentName = bus.getCategory() == null ? "" : bus.getCategory().getName();
            if (DEFAULT_CATEGORIES.containsKey(currentName))
                continue;

            int fleetNumber = Integer.parseInt(registration.substring(registration.length() - 4));
            String name = fleetNumber % 10 == 0 ? "Sleeper" : fleetNumber % 10 <= 5 ? "Normal" : "Luxury";
            bus.setCategory(standardCategories.get(name));
            int seatsPerRow = name.equals("Luxury") ? 3 : name.equals("Sleeper") ? 2 : 4;
            String plan = name.equals("Luxury") ? "2+1 premium"
                    : name.equals("Sleeper") ? "1+1 sleeper berths" : "2+2 non-AC";
            int premiumSeats = name.equals("Luxury") ? Math.min(bus.getTotalSeats(), seatsPerRow) : 0;
            String categoryKey = name.equals("Luxury") ? "PREMIUM" : name.equals("Sleeper") ? "BERTH" : "STANDARD";
            StringBuilder numbers = new StringBuilder();
            for (int seat = 1; seat <= (premiumSeats > 0 ? premiumSeats : bus.getTotalSeats()); seat++) {
                if (seat > 1)
                    numbers.append(',');
                numbers.append('"').append(seat).append('"');
            }
            bus.setSeatLayout("{\"rows\":" + (int) Math.ceil(bus.getTotalSeats() / (double) seatsPerRow)
                    + ",\"seatsPerRow\":" + seatsPerRow + ",\"layout\":\"" + plan
                    + "\",\"totalSeats\":" + bus.getTotalSeats() + ",\"airConditioned\":" + !name.equals("Normal")
                    + ",\"categories\":{\"" + categoryKey + "\":[" + numbers + "]}}");
            if (name.equals("Normal")) {
                bus.setModel(bus.getModel().replaceAll("(?i)( AC Intercity Coach| Luxury Seater)$",
                        " Non-AC Roadways Seater"));
            } else if (name.equals("Sleeper") && !bus.getModel().toLowerCase().contains("sleeper")) {
                bus.setModel("Sleeper " + bus.getModel());
            }
            changedBuses.add(bus);
        }
        if (!changedBuses.isEmpty())
            busRepository.saveAll(changedBuses);

        // Add an idempotent nationwide fleet on existing-db installations too.
        // Registration prefixes cover all state route networks; buses are linked
        // to every active route so the fleet is available to route scheduling.
        String[] states = { "AP", "AR", "AS", "BR", "CG", "GA", "GJ", "HR", "HP", "JH", "KA", "KL", "MP", "MH", "MN",
                "ML", "MZ", "NL", "OD", "PB", "RJ", "SK", "TN", "TS", "TR", "UP", "UK", "WB" };
        String[] manufacturers = { "Volvo", "Scania", "BharatBenz", "Tata", "Ashok Leyland", "Eicher" };
        Map<String, BusCategory> categoryByName = new HashMap<>();
        categoryRepository.findAll().forEach(category -> categoryByName.put(category.getName(), category));
        Map<String, Bus> existingByRegistration = new HashMap<>();
        busRepository.findAll().forEach(bus -> existingByRegistration.put(bus.getRegistrationNumber(), bus));
        var activeRoutes = routeRepository.findAll().stream().filter(Route::isActive).toList();
        ArrayList<Bus> fleetToAdd = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            for (String type : new String[] { "Luxury", "Sleeper" }) {
                int serial = (type.equals("Luxury") ? 3000 : 4000) + i;
                String registration = states[(i - 1) % states.length] + "-01-BK-" + serial;
                if (existingByRegistration.containsKey(registration))
                    continue;
                boolean luxury = type.equals("Luxury");
                int seatsPerRow = luxury ? 3 : 2;
                int totalSeats = 36;
                String layout = luxury ? "2+1 premium" : "1+1 sleeper berths";
                String seatCategories = luxury ? "\"PREMIUM\":[\"1\",\"2\",\"3\"]" : "\"BERTH\":[]";
                Bus bus = new Bus();
                bus.setRegistrationNumber(registration);
                bus.setManufacturer(manufacturers[(i - 1) % manufacturers.length]);
                bus.setModel(luxury ? "Luxury Premium 2+1 Intercity Coach" : "AC Sleeper Berth Coach 1+1");
                bus.setYear((short) (2023 + i % 4));
                bus.setTotalSeats(totalSeats);
                bus.setStatus(BusStatus.ACTIVE);
                bus.setCategory(categoryByName.get(type));
                bus.setSeatLayout("{\"rows\":" + (totalSeats / seatsPerRow) + ",\"seatsPerRow\":" + seatsPerRow
                        + ",\"layout\":\"" + layout + "\",\"totalSeats\":" + totalSeats
                        + ",\"airConditioned\":true,\"categories\":{" + seatCategories + "}}");
                bus.getRoutes().addAll(activeRoutes);
                fleetToAdd.add(bus);
            }
        }
        if (!fleetToAdd.isEmpty())
            busRepository.saveAll(fleetToAdd);

        // Fleet entries alone are not bookable. Existing databases already have
        // trips, so add category-specific schedules across each active Indian
        // route as well. Bus/route/day/direction checks make startup repeatable.
        if (seedTrips) {
            System.out.println("========== BUS TRIP GENERATION STARTED ==========");
            int insertedTripCount = jdbcTemplate.update(
                    CATEGORY_TRIPS_SQL,
                    java.sql.Date.valueOf(LocalDate.now(ZoneId.of("Asia/Kolkata"))));
            System.out.println("Total new trips inserted: " + insertedTripCount);
        } else {
            System.out.println("Demo trip generation skipped. Set buskaroo.initializer.seed-trips=true only when required.");
        }

        if (adjustDepartures) {
            System.out.println("========== DEPARTURE NORMALIZATION STARTED ==========");
            int adjustedTripCount = separateDuplicateDepartures();
            System.out.println("Future unbooked trips given distinct departure times: " + adjustedTripCount);
        } else {
            System.out.println("Departure normalization skipped to avoid memory-heavy startup processing.");
        }

    }

    /**
     * Give future, unbooked services on the same route and direction distinct
     * customer-visible departure minutes. Existing tickets are never moved.
     */
    private int separateDuplicateDepartures() {
        // Preflight based on the number of stop pairs across ALL future trips.
        // Skip rather than exhaust the JVM heap or partially move departures.
        Long estimatedKeys = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(stop_counts.stop_count * (stop_counts.stop_count - 1)), 0)
                FROM trip t
                JOIN (
                    SELECT route_id, COUNT(*)::bigint AS stop_count
                    FROM route_stop
                    GROUP BY route_id
                ) stop_counts ON stop_counts.route_id = t.route_id
                WHERE t.status = 'SCHEDULED'
                  AND t.departure_datetime > NOW()
                """, Long.class);
        long keyCount = estimatedKeys == null ? 0L : estimatedKeys;
        if (keyCount > MAX_DEPARTURE_KEYS) {
            System.out.println("Skipping departure normalization: estimated " + keyCount
                    + " segment keys exceed safe limit " + MAX_DEPARTURE_KEYS
                    + ". Run a batched database maintenance task instead.");
            return 0;
        }

        String findSchedules = """
                SELECT t.id,
                       t.route_id,
                       t.reverse,
                       t.departure_datetime AT TIME ZONE 'Asia/Kolkata' AS departure_local,
                       EXTRACT(EPOCH FROM (t.arrival_datetime - t.departure_datetime)) AS duration_seconds,
                       EXISTS (SELECT 1 FROM reservation r WHERE r.trip_id = t.id) AS has_reservations,
                       rs.sequence_order,
                       rs.departure_offset_minutes,
                       rs.arrival_offset_minutes,
                       terminal.name AS terminal_name
                FROM trip t
                JOIN route_stop rs ON rs.route_id = t.route_id
                JOIN terminal ON terminal.id = rs.terminal_id
                WHERE t.status = 'SCHEDULED'
                  AND t.departure_datetime > NOW()
                ORDER BY EXISTS (SELECT 1 FROM reservation r WHERE r.trip_id = t.id) DESC,
                         (t.departure_datetime AT TIME ZONE 'Asia/Kolkata')::date,
                         t.departure_datetime,
                         t.route_id,
                         t.reverse,
                         t.id,
                         rs.sequence_order
                """;

        Map<Long, ScheduledTripTime> schedulesById = new LinkedHashMap<>();
        jdbcTemplate.query(findSchedules, rs -> {
            long id = rs.getLong("id");
            ScheduledTripTime schedule = schedulesById.get(id);
            if (schedule == null) {
                schedule = new ScheduledTripTime(
                        id,
                        rs.getBoolean("reverse"),
                        rs.getObject("departure_local", LocalDateTime.class),
                        rs.getLong("duration_seconds"),
                        rs.getBoolean("has_reservations"));
                schedulesById.put(id, schedule);
            }
            String terminalName = rs.getString("terminal_name");
            if (terminalName != null) {
                schedule.stops().add(new StopTiming(
                        rs.getInt("sequence_order"),
                        rs.getInt("departure_offset_minutes"),
                        rs.getInt("arrival_offset_minutes"),
                        terminalName));
            }
        });

        // Store keys only, not trip objects; otherwise each occupied key retains
        // the entire ScheduledTripTime and its list of route stops.
        Set<SegmentTimeKey> occupiedDepartures = new HashSet<>();
        int updated = 0;
        ZoneId india = ZoneId.of("Asia/Kolkata");

        for (ScheduledTripTime schedule : schedulesById.values()) {
            LocalDateTime original = schedule.departureLocal();
            Set<SegmentTimeKey> originalKeys = segmentTimeKeys(schedule, original);

            // Reserved trips are placed first by the query and left unchanged.
            if (schedule.hasReservations()) {
                occupiedDepartures.addAll(originalKeys);
                continue;
            }

            LocalDateTime adjusted = original;
            int attempt = 0;
            Set<SegmentTimeKey> adjustedKeys = originalKeys;
            while (hasScheduleTimeConflict(adjustedKeys, occupiedDepartures) && attempt < 96) {
                attempt++;
                adjusted = original.plusMinutes(attempt * 15L);
                adjustedKeys = segmentTimeKeys(schedule, adjusted);
            }

            if (adjusted.toLocalDate().equals(original.toLocalDate())
                    && !hasScheduleTimeConflict(adjustedKeys, occupiedDepartures)) {
                occupiedDepartures.addAll(adjustedKeys);
                if (!adjusted.equals(original)) {
                    OffsetDateTime departure = adjusted.atZone(india).toOffsetDateTime();
                    OffsetDateTime arrival = departure.plusSeconds(schedule.durationSeconds());
                    jdbcTemplate.update(
                            "UPDATE trip SET departure_datetime = ?, arrival_datetime = ?, updated_at = NOW() WHERE id = ?",
                            departure, arrival, schedule.id());
                    updated++;
                }
            } else {
                // Leave a schedule unchanged rather than move it into another service day.
                occupiedDepartures.addAll(originalKeys);
            }
        }
        return updated;
    }

    private boolean hasScheduleTimeConflict(
            Set<SegmentTimeKey> keys,
            Set<SegmentTimeKey> occupiedDepartures) {
        return keys.stream().anyMatch(occupiedDepartures::contains);
    }

    private Set<SegmentTimeKey> segmentTimeKeys(
            ScheduledTripTime schedule, LocalDateTime baseDeparture) {
        Set<SegmentTimeKey> keys = new HashSet<>();
        List<StopTiming> stops = schedule.stops();
        int maxOffset = stops.stream().mapToInt(StopTiming::departureOffsetMinutes).max().orElse(0);

        for (int first = 0; first < stops.size(); first++) {
            for (int second = first + 1; second < stops.size(); second++) {
                StopTiming earlier = stops.get(first);
                StopTiming later = stops.get(second);
                String origin;
                String destination;
                LocalDateTime segmentDeparture;
                LocalDateTime segmentArrival;

                if (!schedule.reverse()) {
                    origin = earlier.terminalName();
                    destination = later.terminalName();
                    segmentDeparture = baseDeparture.plusMinutes(earlier.departureOffsetMinutes());
                    segmentArrival = baseDeparture.plusMinutes(later.arrivalOffsetMinutes());
                } else {
                    origin = later.terminalName();
                    destination = earlier.terminalName();
                    segmentDeparture = baseDeparture.plusMinutes(maxOffset - later.arrivalOffsetMinutes());
                    segmentArrival = baseDeparture.plusMinutes(maxOffset - earlier.departureOffsetMinutes());
                }

                String normalizedOrigin = origin.trim().toLowerCase(java.util.Locale.ROOT);
                String normalizedDestination = destination.trim().toLowerCase(java.util.Locale.ROOT);
                keys.add(new SegmentTimeKey(
                        normalizedOrigin,
                        normalizedDestination,
                        segmentDeparture.truncatedTo(ChronoUnit.MINUTES),
                        true));
                keys.add(new SegmentTimeKey(
                        normalizedOrigin,
                        normalizedDestination,
                        segmentArrival.truncatedTo(ChronoUnit.MINUTES),
                        false));
            }
        }
        return keys;
    }

    private record StopTiming(
            int sequenceOrder,
            int departureOffsetMinutes,
            int arrivalOffsetMinutes,
            String terminalName) {}

    private record SegmentTimeKey(String origin, String destination, LocalDateTime timeMinute, boolean departure) {}

    private static final class ScheduledTripTime {
        private final long id;
        private final boolean reverse;
        private final LocalDateTime departureLocal;
        private final long durationSeconds;
        private final boolean hasReservations;
        private final List<StopTiming> stops = new ArrayList<>();

        private ScheduledTripTime(long id, boolean reverse, LocalDateTime departureLocal,
                long durationSeconds, boolean hasReservations) {
            this.id = id;
            this.reverse = reverse;
            this.departureLocal = departureLocal;
            this.durationSeconds = durationSeconds;
            this.hasReservations = hasReservations;
        }

        private long id() { return id; }
        private boolean reverse() { return reverse; }
        private LocalDateTime departureLocal() { return departureLocal; }
        private long durationSeconds() { return durationSeconds; }
        private boolean hasReservations() { return hasReservations; }
        private List<StopTiming> stops() { return stops; }
    }

    private static final String CATEGORY_TRIPS_SQL = """
            WITH RECURSIVE days(day_offset) AS (
                SELECT 0 UNION ALL SELECT day_offset + 1 FROM days WHERE day_offset < 59
            ), buses AS (
                SELECT b.id, c.name AS category_name,
                       ROW_NUMBER() OVER (
                           PARTITION BY c.name
                           ORDER BY b.registration_number
                       ) AS fleet_number
                FROM bus b
                JOIN bus_category c ON c.id = b.category_id
                WHERE c.name IN ('Normal', 'Luxury', 'Sleeper')
                  AND b.status = 'ACTIVE'
            ), fleet_sizes AS (
                SELECT category_name, COUNT(*) AS fleet_size
                FROM buses
                GROUP BY category_name
            ), drivers AS (
                SELECT user_id,
                       ROW_NUMBER() OVER (ORDER BY user_id) - 1 AS driver_number
                FROM driver
            ), driver_count AS (
                SELECT COUNT(*) AS total FROM drivers
            ), plans(category_name, is_reverse, departure_time) AS (
                VALUES
                    ('Normal', false, TIME '07:15'),
                    ('Normal', true, TIME '11:15'),
                    ('Luxury', false, TIME '06:15'),
                    ('Luxury', true, TIME '10:15'),
                    ('Sleeper', false, TIME '20:15'),
                    ('Sleeper', true, TIME '22:15')
            ), candidates AS (
                SELECT r.id AS route_id,
                       bus.id AS bus_id,
                       driver.user_id AS driver_id,
                       p.is_reverse,
                       (?::date + d.day_offset + p.departure_time)
                           AT TIME ZONE 'UTC' AS departure_datetime,
                       GREATEST(
                           COALESCE(MAX(rs.departure_offset_minutes), 180),
                           60
                       ) AS duration_minutes
                FROM route r
                JOIN route_stop rs ON rs.route_id = r.id
                JOIN terminal terminal
                    ON terminal.id = rs.terminal_id
                   AND terminal.country = 'India'
                CROSS JOIN days d
                CROSS JOIN plans p
                JOIN fleet_sizes fs
                    ON fs.category_name = p.category_name
                   AND fs.fleet_size > 0
                JOIN buses bus
                    ON bus.category_name = p.category_name
                   AND bus.fleet_number =
                       ((r.id + d.day_offset +
                         CASE WHEN p.is_reverse THEN 13 ELSE 0 END)
                        % fs.fleet_size) + 1
                CROSS JOIN driver_count dc
                JOIN drivers driver
                    ON driver.driver_number =
                       ((r.id + d.day_offset +
                         CASE WHEN p.is_reverse THEN 1 ELSE 0 END)
                        % dc.total)
                WHERE r.is_active = true
                GROUP BY r.id, bus.id, driver.user_id,
                         p.is_reverse, d.day_offset, p.departure_time
            )
            INSERT INTO trip (
                id, route_id, bus_id, driver_id, price_koefficient,
                reverse, departure_datetime, arrival_datetime,
                status, created_at, updated_at
            )
            SELECT
                nextval(pg_get_serial_sequence('trip', 'id')),
                c.route_id,
                c.bus_id,
                c.driver_id,
                1.00,
                c.is_reverse,
                c.departure_datetime,
                c.departure_datetime +
                    c.duration_minutes * INTERVAL '1 minute',
                'SCHEDULED',
                NOW(),
                NOW()
            FROM candidates c
            WHERE NOT EXISTS (
                SELECT 1
                FROM trip existing
                WHERE existing.route_id = c.route_id
                  AND existing.bus_id = c.bus_id
                  AND existing.reverse = c.is_reverse
                  AND (existing.departure_datetime AT TIME ZONE 'Asia/Kolkata')::date =
                      (c.departure_datetime AT TIME ZONE 'Asia/Kolkata')::date
            )
            """;

}
