Create the pom_info table:

```sql
CREATE TABLE `pom_info` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `library_id` int(11) NOT NULL,
  `has_assembly_plugin` tinyint(1) DEFAULT 0,
  `has_shade_plugin` tinyint(1) DEFAULT 0,
  `has_dependency_reduced_pom` tinyint(1) DEFAULT 0,
  `has_minimize_jar` tinyint(1) DEFAULT 0,
  `has_relocations` tinyint(1) DEFAULT 0,
  `has_filters` tinyint(1) DEFAULT 0,
  `has_transformers` tinyint(1) DEFAULT 0,
  `parent_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_library_id` (`library_id`),
  KEY `fk_parent_id` (`parent_id`),
  CONSTRAINT `fk_library_info` FOREIGN KEY (`library_id`) REFERENCES `libraries` (`id`),
  CONSTRAINT `fk_parent_id` FOREIGN KEY (`parent_id`) REFERENCES `libraries` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
```



CREATE TABLE `signatures_memory` (
`id` int(11) NOT NULL AUTO_INCREMENT,
`library_id` int(11) NOT NULL,
`class_hash` bigint(20) NOT NULL,
`class_crc` bigint(20) NOT NULL,
PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1175227255 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci