ALTER TABLE t_category
    DROP INDEX uk_category_code,
    DROP COLUMN category_code;

ALTER TABLE t_test
    DROP INDEX uk_test_code,
    DROP COLUMN test_code;
