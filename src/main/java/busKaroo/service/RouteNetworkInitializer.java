package busKaroo.service;

import busKaroo.dao.RouteRepository;
import busKaroo.model.Route;
import busKaroo.model.Terminal;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Fills route-stop data for seeded nationwide corridors before trip schedules are created. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RouteNetworkInitializer implements ApplicationRunner {
    private final RouteRepository routeRepository;
    private final JdbcTemplate jdbcTemplate;

    public RouteNetworkInitializer(RouteRepository routeRepository, JdbcTemplate jdbcTemplate) {
        this.routeRepository = routeRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, Terminal> terminalsByCity = new HashMap<>();
        jdbcTemplate.query("SELECT id, city, latitude, longitude FROM terminal WHERE country = 'India'",
                rs -> {
                    Terminal terminal = new Terminal();
                    terminal.setId(rs.getLong("id"));
                    terminal.setCity(rs.getString("city"));
                    terminal.setLatitude(rs.getBigDecimal("latitude"));
                    terminal.setLongitude(rs.getBigDecimal("longitude"));
                    terminalsByCity.putIfAbsent(normalize(terminal.getCity()), terminal);
                });

        int insertedStops = 0;
        for (Route route : routeRepository.findAll()) {
            if (!route.isActive()) continue;
            Integer existingStops = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM route_stop WHERE route_id = ?", Integer.class, route.getId());
            if (existingStops != null && existingStops > 0) continue;

            String[] cityNames = route.getName().split("\\s+-\\s+");
            List<Terminal> stops = java.util.Arrays.stream(cityNames)
                    .map(name -> terminalsByCity.get(normalize(name)))
                    .toList();
            if (stops.size() < 2 || stops.stream().anyMatch(java.util.Objects::isNull)) continue;

            double distance = 0;
            int elapsedMinutes = 0;
            Terminal previous = null;
            for (int i = 0; i < stops.size(); i++) {
                Terminal stop = stops.get(i);
                if (previous != null) {
                    double segmentKm = distanceKm(previous, stop);
                    distance += segmentKm;
                    elapsedMinutes += Math.max(45, (int) Math.round(segmentKm / 55.0 * 60));
                }
                int arrival = elapsedMinutes;
                int departure = i == stops.size() - 1 ? arrival : arrival + 10;
                jdbcTemplate.update("""
                        INSERT INTO route_stop
                          (route_id, terminal_id, sequence_order, arrival_offset_minutes,
                           departure_offset_minutes, distance_from_origin, base_price_from_origin)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT DO NOTHING
                        """,
                        route.getId(), stop.getId(), i + 1, arrival, departure,
                        BigDecimal.valueOf(distance).setScale(1, RoundingMode.HALF_UP),
                        BigDecimal.valueOf(distance * 1.8).setScale(2, RoundingMode.HALF_UP));
                insertedStops++;
                previous = stop;
            }
        }
        if (insertedStops > 0) {
            System.out.println("Created " + insertedStops + " route stops for Indian corridors missing stop data.");
        }
    }

    private static String normalize(String city) {
        return city == null ? "" : city.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static double distanceKm(Terminal from, Terminal to) {
        double lat1 = Math.toRadians(from.getLatitude().doubleValue());
        double lat2 = Math.toRadians(to.getLatitude().doubleValue());
        double latDiff = lat2 - lat1;
        double lonDiff = Math.toRadians(to.getLongitude().doubleValue() - from.getLongitude().doubleValue());
        double a = Math.sin(latDiff / 2) * Math.sin(latDiff / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(lonDiff / 2) * Math.sin(lonDiff / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
