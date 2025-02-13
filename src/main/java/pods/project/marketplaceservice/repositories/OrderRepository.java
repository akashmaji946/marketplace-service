package pods.project.marketplaceservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pods.project.marketplaceservice.entities.Order;
import pods.project.marketplaceservice.entities.Product;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Integer> {

    @Query(value = "SELECT o FROM Order o WHERE o.id = :id")
    List<Order> findByOrderId(@Param("id") Integer id);

    @Query(value = "SELECT o FROM Order o")
    List<Product> findAllOrders();

    @Query("SELECT o FROM Order o WHERE o.user_id=:id")
    List<Order> getOrdersByUser_id(Integer id);
}
