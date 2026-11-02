package com.kyumin.dotoree.config;

import org.apache.ibatis.type.TypeHandlerRegistry;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.kyumin.dotoree.domain.AccountType;
import com.kyumin.dotoree.domain.CategoryType;
import com.kyumin.dotoree.domain.CodeEnum;
import com.kyumin.dotoree.domain.OccurrenceStatus;
import com.kyumin.dotoree.domain.RepeatType;
import com.kyumin.dotoree.domain.UserStatus;

// 한 글자 코드 컬럼에 대응하는 enum 은 여기 한 줄씩 등록한다 — 새 코드 enum 을 만들면 이 목록에 추가
// (빠뜨리면 MyBatis 가 이름("ACTIVE")으로 저장하려 하므로, 통합 테스트의 첫 INSERT 에서 바로 드러난다)
@Configuration
public class MyBatisConfig {

	@Bean
	public ConfigurationCustomizer codeEnumTypeHandlers() {
		return configuration -> {
			TypeHandlerRegistry registry = configuration.getTypeHandlerRegistry();
			register(registry, UserStatus.class);
			register(registry, CategoryType.class);
			register(registry, AccountType.class);
			register(registry, RepeatType.class);
			register(registry, OccurrenceStatus.class);
		};
	}

	private static <E extends Enum<E> & CodeEnum> void register(TypeHandlerRegistry registry, Class<E> type) {
		registry.register(type, new CodeEnumTypeHandler<>(type));
	}
}
