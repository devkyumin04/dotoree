package com.kyumin.dotoree;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// 메일은 로그로 — 테스트에서 SES 를 부르지 않는다 (ADR-068)
@SpringBootTest(properties = "dotoree.mail.sender=log")
class DotoreeApplicationTests {

	@Test
	void contextLoads() {
	}

}
