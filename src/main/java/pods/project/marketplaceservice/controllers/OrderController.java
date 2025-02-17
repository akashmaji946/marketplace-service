package pods.project.marketplaceservice.controllers;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import pods.project.marketplaceservice.entities.Order;
import pods.project.marketplaceservice.entities.OrderItem;
import pods.project.marketplaceservice.entities.Product;
import pods.project.marketplaceservice.repositories.OrderRepository;
import pods.project.marketplaceservice.repositories.ProductsRepository;

import java.util.*;

@RestController
public class OrderController {


    private final OrderRepository orderRepository;
    private final ProductsRepository productsRepository;
    private RestTemplate restTemplate;

    @Value("${host.url}")
    String localhost;

    @Autowired
    public OrderController(OrderRepository orderRepository, ProductsRepository productsRepository) {
            this.orderRepository = orderRepository;
        this.restTemplate = new RestTemplate();
        this.productsRepository = productsRepository;
    }

    @GetMapping("orders")
    public ResponseEntity<List<Product>> getAllOrders(){
        List<Product> orders = new ArrayList<>();
        orders = orderRepository.findAllOrders();
        return  ResponseEntity.ok().body(orders);
    }

    @GetMapping("/orders/users/{id}")
    public ResponseEntity<List<Map<String, Object>>> getAllOrdersByUserId(@PathVariable Integer id){
        List<Order> orders = new ArrayList<>();
        orders = orderRepository.getOrdersByUser_id(id);

        return ResponseEntity.status(HttpStatus.OK).body(flattenOrders(orders));

    }

    @DeleteMapping("/orders/{id}")
    public ResponseEntity<?> deleteOrder(@PathVariable Integer id){
        List<Order> orders = orderRepository.findByOrderId(id);
        if(orders.isEmpty()){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(orderNotFound(id, false));
        }
        Order order = orders.get(0);

        if(order.getStatus().equals("CANCELLED") || order.getStatus().equals("DELIVERED")){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(orderStateDifferent(id, order.getStatus()));
        }
        order.setStatus("CANCELLED");
        orderRepository.save(order);

        Integer user_id = order.getUser_id();
        Integer total_price = order.getTotal_price();
        updateWallet(user_id, total_price, "credit");


        List<OrderItem> orderItems =  order.getItems();
        for(OrderItem orderItem : orderItems){
            Integer quantity = orderItem.getQuantity();
            Integer product_id = orderItem.getProduct_id();
            List<Product> products =  productsRepository.findProductByIdIs(product_id);
            Integer old_quantity = products.get(0).getStock_quantity();
            productsRepository.updateQuantity(product_id, old_quantity + quantity);

        }
        return ResponseEntity.status(HttpStatus.OK).body(flattenOrders(orders));
    }



    @GetMapping("/orders/{id}")
    public ResponseEntity<?> getProductById(@PathVariable Integer id){
        List<Order> orders = orderRepository.findByOrderId(id);
        if(orders.isEmpty()){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(orderNotFound(id, false));
        }
        return  ResponseEntity.ok().body(flattenOrder(orders.get(0)));
    }

    @PutMapping("/orders/{id}")
    public ResponseEntity<?> updateProductById(@PathVariable Integer id, @RequestBody Map<String, Object> request){
        List<Order> orders = orderRepository.findByOrderId(id);
        if(orders.isEmpty()){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(orderNotFound(id, false));
        }
//        return  ResponseEntity.ok().body(flattenOrder(orders.get(0)));
        Integer order_id = Integer.parseInt(request.get("order_id").toString());
        String status = request.get("status").toString();
        Order order = orders.get(0);

        if(id != order_id){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(orderNotFound(id, true));
        }

        if(!status.equals("DELIVERED") || !order.getStatus().equals("PLACED")){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(badOrderPut(id));
        }

        order.setStatus(status);
        Order savedOrder = orderRepository.save(order);

        Map<String,Object> map = new HashMap<>();
        map.put("order_id",savedOrder.getId());
        map.put("status",savedOrder.getStatus());

        return  ResponseEntity.ok().body(map);

    }

    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> request){

        Integer user_id = Integer.parseInt(request.get("user_id").toString());
        System.out.println("USER ID: " + user_id);
        boolean userExists = getUserById(user_id, false);
        if(!userExists){
            System.out.println("NOT HERE");
            return  ResponseEntity.status(HttpStatus.BAD_REQUEST).body(userNotFound(user_id));
        }

        List<Map<String, Object>> itemsList = (List<Map<String, Object>>) request.get("items");
        Map<Integer, Integer> productQuantityMap = new HashMap<>();
        for (Map<String, Object> item : itemsList) {
            Integer productId = Integer.parseInt(item.get("product_id").toString());
            Integer quantity = Integer.parseInt(item.get("quantity").toString());
            productQuantityMap.put(productId, quantity);
        }

        List<Map<String, Integer>>  productsQuantityList = new ArrayList<>();

        Order order = new Order();
        List<OrderItem> orderItems = new ArrayList<>();
        Integer totalPrice = 0;

        for(Map.Entry<Integer, Integer> entry : productQuantityMap.entrySet()){
            Integer id = entry.getKey();
            Integer quantity = entry.getValue();
            List<Product> products = productsRepository.findProductByIdIs(id);
            if(products.isEmpty()){
                return  ResponseEntity.status(HttpStatus.BAD_REQUEST).body(productNotFound(id, false, -1, -1));
            }
            Integer quantityLeft = products.get(0).getStock_quantity();
            if(quantityLeft < quantity){
                return  ResponseEntity.status(HttpStatus.BAD_REQUEST).body(productNotFound(id, true, quantity, quantityLeft));
            }
            Integer productPrice = products.get(0).getPrice();

            OrderItem orderItem = new OrderItem();
            orderItem.setQuantity(quantity);
            orderItem.setProduct_id(id);
            orderItem.setOrder_id(order);

            orderItems.add(orderItem);


            totalPrice += productPrice * quantity;

            Map<String, Integer> productUpdated = new HashMap<>();
            productUpdated.put("product_id", id);
            productUpdated.put("quantity", quantityLeft - quantity);

            productsQuantityList.add(productUpdated);

        }
        // get discount availability
        boolean discountAvailed = getUserById(user_id, true);
        if(!discountAvailed){
            totalPrice = totalPrice - (int)(totalPrice * 0.10);
        }

        // check if user has sufficient balance
        Integer balance = getUserBalanceById(user_id);
        if(balance == -1){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User with id=" + user_id + " has NO wallet. Please create wallet first.");
        }
        if(balance < totalPrice){
            return   ResponseEntity.status(HttpStatus.BAD_REQUEST).body(userInsufficientFunds(user_id, totalPrice, balance));
        }

        // update user's wallet
        System.out.println("NEW BAL is " + (totalPrice));
        boolean updatedWallet = updateWallet(user_id, totalPrice, "debit");

        // order can be placed
        order.setItems(orderItems);
        order.setStatus("PLACED");
        order.setUser_id(user_id);
        order.setTotal_price(totalPrice);

        Order savedOrder = orderRepository.save(order);
//        orderRepository.placeOrder(order);

        // update the user too
        boolean userUpdated =  updateUser(user_id, true);

        boolean updatedProducts =  updateProducts(savedOrder.getId(), productsQuantityList);

        if(updatedWallet && userUpdated && updatedProducts){
            Map<String,Object> map = new HashMap<>();
            map.put("order_id", savedOrder.getId());
            map.put("user_id", user_id);
            map.put("total_price", totalPrice);
            map.put("status", "PLACED");
            map.put("items", flattenOrderItems(orderItems));
            System.out.printf(map.toString());
            return ResponseEntity.status(HttpStatus.OK).body(map);
        }


        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internel Error! This is on us.");
    }

    @DeleteMapping("/marketplace")
    public ResponseEntity<?> deleteAll(){
        orderRepository.deleteAllPlaced();
        return ResponseEntity.status(HttpStatus.OK).body("All Placed Orders Cancelled.");
    }

    @DeleteMapping("/marketplace/users/{id}")
    public ResponseEntity<?> deleteAllPlacedForUser(@PathVariable Integer id){
//        Integer count =  orderRepository.deleteAllPlacedForUser(id);
//        if(count == 0){
//            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No PLACED Order Found for User with id=" + id);
//        }

        List<Order> orders =  orderRepository.getPlacedOrdersForUser(id);
        if(orders.isEmpty()){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No PLACED Order Found for User with id=" + id);
        }
        for(Order order : orders){
            order.setStatus("CANCELLED");
            orderRepository.save(order);
            Integer user_id = order.getUser_id();
            Integer total_price = order.getTotal_price();
            updateWallet(user_id, total_price, "credit");
            List<OrderItem> orderItems =  order.getItems();
            for(OrderItem orderItem : orderItems){
                Integer quantity = orderItem.getQuantity();
                Integer product_id = orderItem.getProduct_id();
                List<Product> products =  productsRepository.findProductByIdIs(product_id);
                Integer old_quantity = products.get(0).getStock_quantity();
                productsRepository.updateQuantity(product_id, old_quantity + quantity);

            }
        }

        return ResponseEntity.status(HttpStatus.OK).body("All PLACED Orders CANCELLED for User with id=" + id);
    }

    private boolean updateWallet(Integer user_id, Integer newBalance, String type) {
        String url = "http://" + localhost +":8081/wallets/" + user_id;


        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Create the body (JSON payload) with two attributes
        Map<String, Object> body = new HashMap<>();
        body.put("action", type);
        body.put("amount", newBalance);

        System.out.println(body.toString());

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                String responseBody = response.getBody();
                if (Objects.nonNull(responseBody) && !responseBody.isEmpty()) {
                    try {
                        JSONObject user = new JSONObject(responseBody);
                        Integer balance = user.getInt("balance");
                        return balance >= 0;

                    } catch (JSONException e) {
                        System.out.println("Error parsing JSON: " + e.getMessage());
                        return false;
                    }
                } else {
                    System.out.println("Response body is empty or null.");
                    return false;
                }
            }


        }
        catch (RestClientException e) {
            System.out.println(e.getMessage());
            throw e;
        }
        return false;

    }

    private String userInsufficientFunds(Integer userId, Integer totalPrice, Integer balance) {
        return "User with id=" + userId + " has insufficient funds: [bill/balance] =  ["+totalPrice + "/"+balance+"]";
    }

    private Integer getUserBalanceById(Integer user_id) {
        String url = "http://" + localhost +":8081/wallets/" + user_id;

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(null, null);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                    String responseBody = response.getBody();
                    if (Objects.nonNull(responseBody) && !responseBody.isEmpty()) {
                        try {
                            JSONObject user = new JSONObject(responseBody);
                            Integer balance = user.getInt("balance");
                            return balance;

                        } catch (JSONException e) {
                            System.out.println("Error parsing JSON: " + e.getMessage());
                            return -1;
                        }
                    } else {
                        System.out.println("Response body is empty or null.");
                        return -1;
                    }
                }


            }
        catch (RestClientException e) {
            System.out.println(e.getMessage());
            return -1;
        }
            return -1;
    }

    private  List<Map<String, Integer>> flattenOrderItems(List<OrderItem> orderItems) {
        List<Map<String, Integer>> orderItemList = new ArrayList<>();
        for( OrderItem orderItem : orderItems){
            Map<String,Integer> productQuantityMap = new HashMap<>();
            productQuantityMap.put("product_id", orderItem.getProduct_id());
            productQuantityMap.put("quantity", orderItem.getQuantity());
            productQuantityMap.put("id", orderItem.getId());

            orderItemList.add(productQuantityMap);
        }
        return orderItemList;
    }

    private List<Map<String, Object>> flattenOrders(List<Order> orders) {
        List<Map<String, Object>> result = new ArrayList<>();
        for( Order order : orders){
            Map<String, Object> flattenOrder = flattenOrder(order);
            result.add(flattenOrder);
        }
        return result;
    }

    private Map<String, Object> flattenOrder(Order order) {
        Map<String,Object> map = new HashMap<>();
        map.put("order_id", order.getId());
        map.put("user_id", order.getUser_id());
        map.put("total_price", order.getTotal_price());
        map.put("status", order.getStatus());
        map.put("items", flattenOrderItems(order.getItems()));
        return map;
    }

    private boolean updateProducts(Integer order_id, List<Map<String, Integer>> discountAvailed) {
        String url = "http://" + localhost +":8082/products";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Create the body (JSON payload) with two attributes
        Map<String, Object> body = new HashMap<>();
        body.put("order_id", order_id);
        body.put("products", discountAvailed);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);


        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                return true;
            }
            return false;
        } catch (RestClientException e) {
            System.out.println(e.getMessage());
            return false;
        }

    }

    private boolean updateUser(Integer userId, boolean discountAvailed) {
        String url = "http://" + localhost +":8080/users";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Create the body (JSON payload) with two attributes
        Map<String, Object> body = new HashMap<>();
        body.put("id", userId);
        body.put("discount_availed", discountAvailed);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                return true;
            }
            return false;
        } catch (RestClientException e) {
            System.out.println(e.getMessage());
            return false;
        }

    }

    private String productNotFound(Integer id, boolean insufficientStock, Integer quantity, Integer quantityLeft) {
        if(insufficientStock){
            return  "Product with id " + id + " has insufficient stock: [wanted/left]=[" + quantity + "/" + quantityLeft + "]";
        }
        return "Product not found with id: " + id;
    }

    private String userNotFound(Integer userId) {
        return "User not found with id: " + userId;

    }

    private String badOrderPut(Integer id) {
        return "Bad Order Update Request. Check status!";
    }

    private String orderStateDifferent(Integer id, String status) {
        return  "Order with id: " + id + " can't be CANCELLED with status: " + status;
    }




    public boolean getUserById(Integer user_id, boolean discountCheck) {
        String url = "http://" + localhost +":8080/users/" + user_id;

//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_JSON);

//        Map<String, Object> requestBody = new HashMap<>();
//        requestBody.put("userId", 123);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(null, null);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {

                if(discountCheck){
                    String responseBody = response.getBody();
                    if (Objects.nonNull(responseBody) && !responseBody.isEmpty()) {
                        try {
                            JSONObject user = new JSONObject(responseBody);
                            boolean discountAvailed = user.getBoolean("discount_availed");
                            System.out.println("Discount availed: " + discountAvailed);
                            return discountAvailed;
                        } catch (JSONException e) {
                            System.out.println("Error parsing JSON: " + e.getMessage());
                            return false;
                        }
                    } else {
                        System.out.println("Response body is empty or null.");
                        return false;
                    }
                }

                System.out.println("User Found Successfully!");
                return true;
            }
            return false;
        } catch (RestClientException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

//    public boolean getUserStatusById(Integer user_id) {
//        String url = "http://" + localhost +":8080/users/discount" + user_id;
//
//        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(null, null);
//
//        try {
//            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);
//
//            if (response.getStatusCode().is2xxSuccessful()) {
//
//                System.out.println("User Found Successfully!");
//                return true;
//            }
//            return false;
//        } catch (RestClientException e) {
//            System.out.println(e.getMessage());
//            return false;
//        }
//    }

    private static String orderNotFound(Integer id, boolean mismatch) {
        if(mismatch){
            return "Order id in path mismatches that in payload";
        }
        return  "Order with id=" + id + " not found";
    }
}
