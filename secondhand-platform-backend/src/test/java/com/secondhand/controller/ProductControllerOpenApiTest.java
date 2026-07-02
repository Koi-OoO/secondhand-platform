package com.secondhand.controller;

import com.secondhand.entity.Order;
import com.secondhand.entity.Product;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductControllerOpenApiTest {

    @Test
    void updateOperationDocumentsEditableFieldValidation() throws NoSuchMethodException {
        Method method = ProductController.class.getMethod("update", Product.class, HttpSession.class);
        Operation operation = method.getAnnotation(Operation.class);

        assertThat(operation).isNotNull();
        assertThat(operation.description())
                .contains("只能修改自己的商品")
                .contains("imageUrls");
    }

    @Test
    void imageUrlsSchemaDocumentsPublishAndUpdateRules() throws NoSuchFieldException {
        Field field = Product.class.getDeclaredField("imageUrls");
        Schema schema = field.getAnnotation(Schema.class);

        assertThat(schema).isNotNull();
        assertThat(schema.description())
                .contains("图片地址列表")
                .contains("发布")
                .contains("编辑");
    }

    @Test
    void productAndOrderSchemasExposeInventoryFields() {
        Field stockField = requireField(Product.class, "stock");
        Field quantityField = requireField(Order.class, "quantity");

        assertThat(stockField.getAnnotation(Schema.class)).isNotNull();
        assertThat(quantityField.getAnnotation(Schema.class)).isNotNull();
    }

    @Test
    void createOrderOperationDocumentsQuantity() {
        Method createMethod = requireMethod(OrderController.class, "create",
                Long.class, Integer.class, String.class, HttpSession.class);
        Operation operation = createMethod.getAnnotation(Operation.class);

        assertThat(operation).isNotNull();
        assertThat(operation.description()).contains("购买数量");
    }

    @Test
    void orderHideOperationsAreDocumented() {
        Method hideBuyerMethod = requireMethod(OrderController.class, "hideBought", Long.class, HttpSession.class);
        Method hideSellerMethod = requireMethod(OrderController.class, "hideSold", Long.class, HttpSession.class);

        Operation hideBuyerOperation = hideBuyerMethod.getAnnotation(Operation.class);
        Operation hideSellerOperation = hideSellerMethod.getAnnotation(Operation.class);

        assertThat(hideBuyerOperation).isNotNull();
        assertThat(hideBuyerOperation.description()).contains("买家").contains("隐藏").contains("卖家仍可见");

        assertThat(hideSellerOperation).isNotNull();
        assertThat(hideSellerOperation.description()).contains("卖家").contains("隐藏").contains("买家仍可见");
    }

    private Method requireMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ex) {
            throw new AssertionError("Expected method " + type.getSimpleName() + "." + name + " to exist", ex);
        }
    }

    private Field requireField(Class<?> type, String name) {
        try {
            return type.getDeclaredField(name);
        } catch (NoSuchFieldException ex) {
            throw new AssertionError("Expected field " + type.getSimpleName() + "." + name + " to exist", ex);
        }
    }
}
