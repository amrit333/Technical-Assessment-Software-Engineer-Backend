package com.inventory.order.service;

import com.inventory.order.exception.DuplicateResourceException;
import com.inventory.order.exception.ResourceNotFoundException;
import com.inventory.order.model.Product;
import com.inventory.order.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    @Autowired
    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    public Product createProduct(Product product) {
        if (productRepository.existsBySku(product.getSku())) {
            throw new DuplicateResourceException("Product with SKU '" + product.getSku() + "' already exists");
        }
        return productRepository.save(product);
    }

    public Product updateProduct(Long id, Product details) {
        Product product = getProductById(id);
        
        // If the SKU is being changed, verify it doesn't conflict with another product
        if (!product.getSku().equals(details.getSku())) {
            if (productRepository.existsBySku(details.getSku())) {
                throw new DuplicateResourceException("Product with SKU '" + details.getSku() + "' already exists");
            }
            product.setSku(details.getSku());
        }

        product.setName(details.getName());
        product.setDescription(details.getDescription());
        product.setPrice(details.getPrice());
        product.setStock(details.getStock());

        return productRepository.save(product);
    }

    public void deleteProduct(Long id) {
        Product product = getProductById(id);
        productRepository.delete(product);
    }
}
