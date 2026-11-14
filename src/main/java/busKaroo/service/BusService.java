package busKaroo.service;

import busKaroo.dao.BusRepository;
import busKaroo.dao.BusCategoryRepository;
import busKaroo.dto.request.BusRequest;
import busKaroo.exception.BusCategoryNotFoundException;
import busKaroo.exception.BusNotFoundException;
import busKaroo.model.Bus;
import busKaroo.model.BusCategory;
import busKaroo.model.enums.BusStatus;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
public class BusService {

    private final BusRepository busRepository;
    private final BusCategoryRepository categoryRepository;

    public BusService(BusRepository busRepository, BusCategoryRepository categoryRepository) {
        this.busRepository = busRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<Bus> findAll() {
        return busRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Bus findById(Long id) {
        return busRepository.findById(id).orElseThrow(() -> new BusNotFoundException(id));
    }

    @Transactional
    public Bus create(BusRequest request) {
        String registration = normalizeRegistration(request.registrationNumber());
        if (busRepository.existsByRegistrationNumberIgnoreCase(registration)) {
            throw duplicateRegistration(registration);
        }

        Bus bus = new Bus();
        applyRequest(bus, request, registration);
        return busRepository.save(bus);
    }

    @Transactional
    public Bus update(Long id, BusRequest request) {
        Bus bus = findById(id);
        String registration = normalizeRegistration(request.registrationNumber());
        if (busRepository.existsByRegistrationNumberIgnoreCaseAndIdNot(registration, id)) {
            throw duplicateRegistration(registration);
        }

        applyRequest(bus, request, registration);
        return busRepository.save(bus);
    }

    @Transactional
    public void updateStatus(Long id, BusStatus status) {
        Bus bus = findById(id);
        bus.setStatus(status);
        busRepository.save(bus);
    }

    @Transactional
    public void delete(Long id) {
        if (!busRepository.existsById(id)) {
            throw new BusNotFoundException(id);
        }
        busRepository.deleteById(id);
    }

    private String normalizeRegistration(String registrationNumber) {
        if (registrationNumber == null || registrationNumber.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Registration number is required.");
        }
        return registrationNumber.trim().toUpperCase(Locale.ROOT);
    }

    private ResponseStatusException duplicateRegistration(String registration) {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "A bus with registration number " + registration + " already exists.");
    }

    private void applyRequest(Bus bus, BusRequest request, String registration) {
        BusCategory category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new BusCategoryNotFoundException(request.categoryId()));
        bus.setRegistrationNumber(registration);
        bus.setModel(request.model().trim());
        bus.setManufacturer(request.manufacturer().trim());
        bus.setYear((short) request.year());
        bus.setTotalSeats(request.totalSeats());
        bus.setSeatLayout(request.seatLayout().trim());
        bus.setStatus(request.status());
        bus.setCategory(category);
    }
}
