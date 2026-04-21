# Shop Products MongoDB Migration

## Scope

This migration moves shop product/item read and write operations in the `shop_orders` service to MongoDB when `MONGODB_ENABLED=true`.

The existing MySQL/JPA product implementation remains the default path when MongoDB is disabled. This allows staged rollout and rollback without removing the current MySQL tables.

## Changed Service

Service changed:

- `shop_orders`

Main business logic:

- `src/main/java/com/msa/shop_orders/provider/shop/service/ShopProductService.java`
- `src/main/java/com/msa/shop_orders/provider/shop/service/ShopProductServiceImpl.java`
- `src/main/java/com/msa/shop_orders/provider/shop/service/MongoShopProductServiceImpl.java`
- `src/main/java/com/msa/shop_orders/provider/shop/service/MongoSequenceService.java`
- `src/main/java/com/msa/shop_orders/provider/shop/service/ShopProductActivityLogService.java`

Controllers:

- `src/main/java/com/msa/shop_orders/provider/shop/controller/ShopProductController.java`
- `src/main/java/com/msa/shop_orders/user/shop/controller/UserShopProductController.java`

Mongo persistence:

- `src/main/java/com/msa/shop_orders/persistence/mongo/document/ShopProductDocument.java`
- `src/main/java/com/msa/shop_orders/persistence/mongo/document/MongoSequenceDocument.java`
- `src/main/java/com/msa/shop_orders/persistence/mongo/repository/ShopProductMongoRepository.java`
- `src/main/java/com/msa/shop_orders/persistence/mongo/repository/ShopProductMongoQueryRepository.java`

User product query service:

- `src/main/java/com/msa/shop_orders/user/shop/service/UserShopProductQueryService.java`
- `src/main/java/com/msa/shop_orders/user/shop/service/MongoUserShopProductQueryService.java`

Config and SQL:

- `src/main/resources/application.yaml`
- `src/main/resources/application-local.yaml`
- `src/main/java/com/msa/shop_orders/security/SecurityConfig.java`
- `src/main/resources/db/migration/V11__shop_products_mongodb_cutover_settings.sql`

## Runtime Switch

Mongo product mode is controlled by:

```bash
MONGODB_ENABLED=true
MONGODB_URI=mongodb://<host>:<port>/<database>
MONGODB_AUTO_INDEX_CREATION=true
```

Default behavior remains:

```bash
MONGODB_ENABLED=false
```

When disabled or unset, `ShopProductServiceImpl` serves product operations from MySQL.

When enabled, `MongoShopProductServiceImpl` serves product operations from MongoDB.

## Mongo Collections

### `products`

Stores shop products/items with embedded child data:

- product core fields
- variants
- inventory
- images
- active promotion
- active coupon
- ratings/order counters
- active/archive state

Important logical identifiers:

- `productId`
- `shopId`
- `shopCategoryId`
- `sku`

Indexes are declared on `ShopProductDocument` and created when `MONGODB_AUTO_INDEX_CREATION=true`.

### `mongo_sequences`

Stores numeric counters used to generate API-compatible IDs for:

- products
- product variants
- product images
- promotions
- coupons

### `shop_product_activity`

Stores product activity audit events when Mongo mode is enabled.

## SQL Migration

Migration file:

```text
src/main/resources/db/migration/V11__shop_products_mongodb_cutover_settings.sql
```

This SQL creates and updates a MySQL audit/cutover marker table:

```text
shop_product_mongodb_cutover_audit
```

It does not copy product data into MongoDB. Product read/write behavior changes only when the service is started with `MONGODB_ENABLED=true`.

### Run With Flyway

If Flyway is enabled for this service:

```bash
FLYWAY_ENABLED=true
```

Then start `shop_orders`. Flyway will apply:

```text
V11__shop_products_mongodb_cutover_settings.sql
```

### Run Manually

Run against the same MySQL database used by `shop_orders`, usually `hyperlocal_platform`:

```bash
mysql -h <mysql-host> -u <username> -p hyperlocal_platform < src/main/resources/db/migration/V11__shop_products_mongodb_cutover_settings.sql
```

## API Changes

### Existing Shop APIs

These continue to use the same shop-facing product route:

```text
GET    /shops/products
POST   /shops/products
PUT    /shops/products/{productId}
POST   /shops/products/{productId}/duplicate
PATCH  /shops/products/{productId}/status
GET    /shops/products/{productId}/activity
```

Added endpoint:

```text
DELETE /shops/products/{productId}
```

Delete is implemented as a soft archive. It sets product active status to false and does not physically delete the product document or MySQL row.

### User/Public APIs

Mongo mode adds public product display APIs:

```text
GET /api/v1/public/shop/products?shopId=<shopId>&categoryId=<categoryId>&search=<text>
GET /api/v1/public/shop/products/{productId}
```

Security permits only `GET /api/v1/public/**` without authentication. Other endpoints remain authenticated.

## Rollout Steps

1. Deploy code with `MONGODB_ENABLED=false`.
2. Run compile/build validation.
3. Apply `V11__shop_products_mongodb_cutover_settings.sql` to MySQL.
4. Ensure MongoDB is reachable from the `shop_orders` runtime environment.
5. Start one lower environment with:

```bash
MONGODB_ENABLED=true
MONGODB_URI=mongodb://<host>:<port>/<database>
MONGODB_AUTO_INDEX_CREATION=true
```

6. Validate shop product create, update, duplicate, archive/remove, and display.
7. Validate user product listing and product detail APIs.
8. Roll out to production after data migration/backfill is complete.

## Data Backfill Requirement

The code supports Mongo-backed product operations, but this change does not include a bulk backfill job from existing MySQL product tables into MongoDB.

Before enabling Mongo mode in production, existing product data should be copied from the current MySQL product tables into the Mongo `products` collection with compatible `productId`, `shopId`, `shopCategoryId`, variant, inventory, image, promotion, and coupon structure.

## Rollback

To return product operations to the existing MySQL path:

```bash
MONGODB_ENABLED=false
```

Then restart `shop_orders`.

The MySQL implementation remains present and is the default fallback. The SQL migration is non-destructive and does not drop or alter product tables.

## Validation Checklist

Run build checks:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn -q -DskipTests compile
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn -q -DskipTests test-compile
```

Validate default mode:

- Start with `MONGODB_ENABLED=false`.
- Existing shop product APIs should continue to use MySQL.
- Application should not require MongoDB for product operations.

Validate Mongo mode:

- Start with `MONGODB_ENABLED=true`.
- Create a product from shop side.
- Update product fields, variants, inventory, images, promotion, and coupon.
- Duplicate product and verify new IDs are generated.
- Archive/remove product and verify it is hidden from user public listing.
- Query public product list and detail APIs.
- Confirm Mongo indexes exist on `products`.

## Known Environment Note

Local `mvn test` may fail if the configured remote MySQL host is not reachable. During implementation, full test startup failed because DNS could not resolve:

```text
multisuperapp1.cituis86a8r2.us-east-1.rds.amazonaws.com
```

The non-DB build checks passed:

```text
mvn -q -DskipTests compile
mvn -q -DskipTests test-compile
```
