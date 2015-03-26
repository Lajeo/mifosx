ALTER TABLE `m_product_loan`
	ADD COLUMN `apply_interest_for_whole_period_on_preclose` TINYINT(1) NOT NULL DEFAULT '0';