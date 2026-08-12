package com.ecillie.fbomanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecillie.modulithfixture.InvalidApplication;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.Violations;

class ApplicationModuleStructureTests {

	private static final Set<String> EXPECTED_MODULES = Set.of("administration", "aircraft", "fleet", "fuel",
			"operations", "parking", "platform", "services", "tasks", "visits", "workforce");

	@Test
	void verifiesApplicationModuleStructure() {
		ApplicationModules.of(FboManagerApplication.class).verify();
	}

	@Test
	void declaresEveryExpectedModuleClosedWithANamedApi() {
		ApplicationModules modules = ApplicationModules.of(FboManagerApplication.class);

		Set<String> moduleNames = modules.stream().map(module -> module.getIdentifier().toString())
				.collect(Collectors.toUnmodifiableSet());

		assertThat(moduleNames).isEqualTo(EXPECTED_MODULES);
		assertThat(modules).allSatisfy(module -> {
			assertThat(module.isOpen()).isFalse();
			assertThat(module.getNamedInterfaces().getByName("api")).isPresent();
		});
	}

	@Test
	void rejectsAnIllegalDependencyOnAnotherModulesInternals() {
		assertThatThrownBy(() -> ApplicationModules
				.of(InvalidApplication.class, ImportOption.Predefined.ONLY_INCLUDE_TESTS).verify())
				.isInstanceOf(Violations.class).hasMessageContaining("InvalidConsumer")
				.hasMessageContaining("AlphaSecret");
	}
}
