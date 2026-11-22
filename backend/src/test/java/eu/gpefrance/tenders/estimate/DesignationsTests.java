package eu.gpefrance.tenders.estimate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The same work written two ways, as in the price schedules of GPE. */
class DesignationsTests {

	@Test
	void writesOutTheAbbreviationsOfTheTrade() {
		assertThat(Designations.key("- Inter SA encastré")).isEqualTo(Designations.key("Interrupteur simple allumage encastré"));
		assertThat(Designations.key("- Inter VV saillie IP 55")).isEqualTo(Designations.key("Interrupteur va-et-vient saillie IP 55"));
		assertThat(Designations.key("- PC 2x10/16A encastrée")).isEqualTo(Designations.key("Prise de courant 2x10/16A encastrées"));
		assertThat(Designations.key("* 3 x 1,5 mm²")).isEqualTo(Designations.key("3G1,5 mm²"));
	}

	@Test
	void keepsWhatMakesTwoWorksDifferent() {
		assertThat(Designations.key("Luminaire type 1")).isNotEqualTo(Designations.key("Luminaire type 2"));
		assertThat(Designations.key("* 3 x 1,5 mm²")).isNotEqualTo(Designations.key("* 3 x 2,5 mm²"));
		assertThat(Designations.key("Inter SA encastré")).isNotEqualTo(Designations.key("Inter SA saillie"));
	}

	/** As measured on Istres: the code alone was wrong on these, by 50 % to 300 %. */
	@Test
	void leavesProjectReferencesAndGenericDesignationsToOthers() {
		assertThat(Designations.isProjectReference("- Luminaire type 6")).isTrue();
		assertThat(Designations.isProjectReference("Luminaire repère A")).isTrue();
		assertThat(Designations.isProjectReference("- Inter SA encastré")).isFalse();
		assertThat(Designations.isPrecise(Designations.key("- Déclencheur Manuel"))).isFalse();
		assertThat(Designations.isPrecise(Designations.key("* 3 x 1,5 mm²"))).isTrue();
		assertThat(Designations.isPrecise(Designations.key("- PC 2x10/16A encastrée"))).isTrue();
	}

	@Test
	void readsTheUnitsAlike() {
		assertThat(Designations.unit("U")).isEqualTo(Designations.unit("unité")).isEqualTo("u");
		assertThat(Designations.unit("ml")).isEqualTo(Designations.unit("m")).isEqualTo("ml");
		assertThat(Designations.unit("Ens")).isEqualTo("ens");
	}

}
