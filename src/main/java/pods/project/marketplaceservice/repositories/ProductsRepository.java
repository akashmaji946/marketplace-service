package pods.project.marketplaceservice.repositories;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pods.project.marketplaceservice.entities.Product;

import java.util.List;

@Repository
public interface ProductsRepository extends JpaRepository<Product, Integer> {

    @Query("SELECT p FROM Product p WHERE p.id = :id")
    List<Product> findByUserId(@Param("id") Integer id);


    List<Product> findProductByIdIs(Integer id);

    @Transactional
    @Modifying
    @Query("UPDATE Product p SET p.stock_quantity=:quantity WHERE p.id=:id")
    void updateQuantity(Integer id, Integer quantity);
}
