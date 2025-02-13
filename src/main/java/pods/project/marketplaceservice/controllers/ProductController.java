package pods.project.marketplaceservice.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import pods.project.marketplaceservice.entities.Product;
import pods.project.marketplaceservice.repositories.ProductsRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
public class ProductController {


    private final ProductsRepository productsRepository;
    private RestTemplate restTemplate;

    @Autowired
    public ProductController(ProductsRepository productsRepository) {
        this.productsRepository = productsRepository;
        this.restTemplate = new RestTemplate();
    }

    @GetMapping("products")
    public ResponseEntity<List<Product>> getAllProducts(){
        List<Product> products = new ArrayList<>();
        products = productsRepository.findAll();
        return  ResponseEntity.ok().body(products);
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<?> getProductById(@PathVariable Integer id){
        List<Product> products = productsRepository.findProductByIdIs(id);
        if(products.isEmpty()){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(productNotFound(id));
        }
        return  ResponseEntity.ok().body(products.get(0));
    }

    private static String productNotFound(Integer id) {
        return  "Product with id=" + id + " not found";
    }

    @PutMapping("/products")
    public ResponseEntity<?> updateProducts(@RequestBody Map<String, Object> request){
        String order_id = request.get("order_id").toString();
        List<Map<String, Integer>> products = (List<Map<String, Integer>>) request.get("products");

        for(Map<String, Integer> product: products){
            Integer id = product.get("product_id");
            Integer quantity = product.get("quantity");
            productsRepository.updateQuantity(id, quantity);
        }

        return ResponseEntity.ok().body(productsUpdated(order_id));
    }

    private String productsUpdated(String orderId) {
        return "Products updated for order with id=" + orderId;
    }

}
