package com.ecommerce.project.service;

import com.ecommerce.project.exceptions.APIException;
import com.ecommerce.project.exceptions.ResourceNotFoundException;
import com.ecommerce.project.model.Cart;
import com.ecommerce.project.model.CartItem;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.payload.CartDTO;
import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.repositories.CartItemRepository;
import com.ecommerce.project.repositories.CartRepository;
import com.ecommerce.project.repositories.ProductRepository;
import com.ecommerce.project.util.AuthUtil;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class CartServiceImpl implements CartService {

    @Autowired
    ProductRepository productRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private AuthUtil authUtil;

    @Autowired
    CartItemRepository cartItemRepository;

    @Autowired
    ModelMapper modelMapper;

    @Override
    public CartDTO addProductToCart(Long productId, Integer quantity) {

        Cart cart = createCart();
        Product product = productRepository.findById(productId)
                .orElseThrow(()->new ResourceNotFoundException("Product", "productId", productId));

        CartItem cartItem = cartItemRepository.findCartItemByProductIdAndCartId(cart.getCartId(), productId);

        if(cartItem != null) {
            throw new APIException("Product " + product.getProductName() + " already exists in the cart");
        }
        if(product.getQuantity()==0) {
            throw new APIException("Product " + product.getProductName() + " is not available");
        }
        if(product.getQuantity()<quantity) {
            throw new APIException("Please, make an order of the " + product.getProductName() +
                    " less than or equal the quantity " + product.getQuantity() + ".");
        }

        CartItem newCartItem = new CartItem();
        newCartItem.setProduct(product);
        newCartItem.setQuantity(quantity);
        newCartItem.setCart(cart);
        newCartItem.setDiscount(product.getDiscount());
        newCartItem.setProductPrice(product.getPrice());
        cartItemRepository.save(newCartItem);

        product.setQuantity(product.getQuantity()-quantity);

        cart.setTotalPrice(cart.getTotalPrice()+ product.getPrice()*quantity);
        cartRepository.save(cart);
        CartDTO cartDTO = modelMapper.map(cart, CartDTO.class);

        List<CartItem> cartItems = cart.getCartItems();
        Stream<ProductDTO> productStream = cartItems.stream().map(item->{
            ProductDTO map = modelMapper.map(item.getProduct(), ProductDTO.class);
            map.setQuantity(item.getQuantity());
            return map;
        });

        cartDTO.setProducts(productStream.toList());
        return cartDTO;
    }

    private Cart createCart() {
        Cart userCart = cartRepository.findCartByEmail(authUtil.loggedInEmail());
        if (userCart != null) {
            return userCart;
        }

        Cart cart = new Cart();
        cart.setTotalPrice(0.0);
        cart.setUser(authUtil.loggedInUser());
        Cart newCart = cartRepository.save(cart);

        return newCart;
    }

    @Override
    public List<CartDTO> getAllCarts() {
        List<Cart> carts = cartRepository.findAll();
        if(carts.isEmpty()) {
            throw new APIException("No cart found");
        }

        List<CartDTO> cartDTOS = carts.stream().map(cart->{
            CartDTO cartDTO = modelMapper.map(cart, CartDTO.class);
            cart.getCartItems().forEach(cartItem -> {
                cartItem.getProduct().setQuantity(cartItem.getQuantity());
            });
            List<ProductDTO> products = cart.getCartItems().stream().map(cartItem->modelMapper.map(cartItem.getProduct(), ProductDTO.class)).collect(Collectors.toList());
            cartDTO.setProducts(products);
            return cartDTO;
            }).collect(Collectors.toList());
        return cartDTOS;
    }

    @Override
    public CartDTO getCartById(String emailId, Long cartId) {
        Cart cart = cartRepository.findCartByEmailAndCartId(emailId, cartId);
        if(cart == null) {
            throw new ResourceNotFoundException("Cart", "cartId", cartId);
        }

        CartDTO cartDTO = modelMapper.map(cart, CartDTO.class);
        cart.getCartItems().forEach(cartItem -> {
            cartItem.getProduct().setQuantity(cartItem.getQuantity());
        });
        List<ProductDTO> productDTOS = cart.getCartItems().stream().map(cartItem->
                modelMapper.map(cartItem.getProduct(), ProductDTO.class)).collect(Collectors.toList());
        cartDTO.setProducts(productDTOS);
        return cartDTO;
    }

    @Transactional
    @Override
    public CartDTO updateProductQuantityInCart(Long productId, Integer quantity){
        String email = authUtil.loggedInEmail();
        Cart userCart = cartRepository.findCartByEmail(email);
        Long cartId = userCart.getCartId();
        Cart cart = cartRepository.findCartById(cartId);

        Product product = productRepository.findById(productId)
                .orElseThrow(()->new ResourceNotFoundException("Product", "productId", productId));

        if(product.getQuantity()==0) {
            throw new APIException("Product " + product.getProductName() + " is not available");
        }

        if(product.getQuantity()<quantity) {
            throw new APIException("Please, make an order of the " + product.getProductName() +
                    " less than or equal the quantity " + product.getQuantity() + ".");
        }

        CartItem cartItem = cartItemRepository.findCartItemByProductIdAndCartId(cartId, productId);

        if(cartItem == null) {
            throw new APIException("Product " + product.getProductName() + " is not available in the cart!");
        }

        int newQuantity = cartItem.getQuantity()+quantity;
        if(newQuantity<0) {
            throw new APIException("The resulting quantity can not be negative");
        }

        if(newQuantity==0){
            deleteProductFromCart(productId, cartId);
        }else{
            cartItem.setProductPrice(product.getSpecialPrice());
            cartItem.setQuantity(cartItem.getQuantity()+quantity);
            cartItem.setDiscount(product.getDiscount());
            cart.setTotalPrice(cart.getTotalPrice()+ cartItem.getProductPrice()*quantity);
            cartRepository.save(cart);
        }
        CartItem updatedCartItem = cartItemRepository.save(cartItem);
        if(updatedCartItem.getQuantity()==0){
            cartItemRepository.deleteById(updatedCartItem.getCartItemId());
        }

        CartDTO cartDTO = modelMapper.map(cart, CartDTO.class);

        List<CartItem> cartItems = cart.getCartItems();
        Stream<ProductDTO> productDTOStream = cartItems.stream().map(item->{
            ProductDTO prd = modelMapper.map(item.getProduct(), ProductDTO.class);
            prd.setQuantity(item.getQuantity());
            return prd;
        });
        cartDTO.setProducts(productDTOStream.toList());
        return cartDTO;
    }

    @Transactional
    @Override
    public String deleteProductFromCart(Long cartId, Long productId){
        Cart cart = cartRepository.findCartById(cartId);
        CartItem cartItem = cartItemRepository.findCartItemByProductIdAndCartId(cartId, productId);

        if(cartItem == null) {
            throw new ResourceNotFoundException("Product", "productId", productId);
        }

        cart.setTotalPrice(cart.getTotalPrice()-cartItem.getProductPrice()*cartItem.getQuantity());
        Product product = cartItem.getProduct();
        product.setQuantity(product.getQuantity()+cartItem.getQuantity());
        cartItemRepository.deleteCartItemByProductIdAndCartId(cartId, productId);
        return "Product " + product.getProductName() + " has been removed from the cart.";
    }

    @Override
    public void updateProductInCarts(Long cartId, Long productId){
        Cart cart = cartRepository.findCartById(cartId);
        Product product = productRepository.findByProductId(productId);

        CartItem cartItem = cartItemRepository.findCartItemByProductIdAndCartId(productId, cartId);

        if(cartItem == null) {
            throw new APIException("Product " + product.getProductName() + " is not available in the cart!");
        }

        double cartPrice = cart.getTotalPrice()-cartItem.getProductPrice()*cartItem.getQuantity();

        cartItem.setProductPrice(product.getSpecialPrice());
        cart.setTotalPrice(cartPrice+cartItem.getProductPrice()*cartItem.getQuantity());
        cartItem = cartItemRepository.save(cartItem);
    }
}
