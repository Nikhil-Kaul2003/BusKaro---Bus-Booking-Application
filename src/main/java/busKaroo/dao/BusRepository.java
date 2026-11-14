
package busKaroo.dao;

import busKaroo.model.Bus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BusRepository extends JpaRepository<Bus, Long> {

    // Check duplicate registration when adding a new bus
    boolean existsByRegistrationNumberIgnoreCase(
            String registrationNumber
    );

    // Check duplicate registration when editing a bus,
    // excluding the bus currently being edited
    boolean existsByRegistrationNumberIgnoreCaseAndIdNot(
            String registrationNumber,
            Long id
    );
}
