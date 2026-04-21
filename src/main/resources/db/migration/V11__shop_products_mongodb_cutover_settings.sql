CREATE TABLE IF NOT EXISTS shop_product_mongodb_cutover_audit (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  cutover_key VARCHAR(120) NOT NULL,
  cutover_value VARCHAR(255) NOT NULL,
  notes VARCHAR(500) NULL,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_shop_product_mongodb_cutover_key (cutover_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO shop_product_mongodb_cutover_audit (cutover_key, cutover_value, notes, updated_at)
VALUES
  ('shop.products.storage', 'MONGODB',
   'Shop product display/add/update/remove reads and writes are served by MongoDB products collection when MONGODB_ENABLED=true. MySQL shop product tables are retained for staged migration and rollback.',
   NOW()),
  ('shop.products.mongo.collection', 'products',
   'Embedded variants, inventory, images, active promotion, and active coupon live in the products collection.',
   NOW())
ON DUPLICATE KEY UPDATE
  cutover_value = VALUES(cutover_value),
  notes = VALUES(notes),
  updated_at = VALUES(updated_at);
