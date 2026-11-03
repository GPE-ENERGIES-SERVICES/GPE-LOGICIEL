package eu.gpefrance.tenders.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class AmountWordsTests {

	private static String words(String amount) {
		return AmountWords.euros(new BigDecimal(amount));
	}

	@Test
	void writesTheAmountsInFrench() {
		assertThat(words("1")).isEqualTo("un euro");
		assertThat(words("21")).isEqualTo("vingt et un euros");
		assertThat(words("71")).isEqualTo("soixante et onze euros");
		assertThat(words("80")).isEqualTo("quatre-vingts euros");
		assertThat(words("81")).isEqualTo("quatre-vingt-un euros");
		assertThat(words("91")).isEqualTo("quatre-vingt-onze euros");
		assertThat(words("200")).isEqualTo("deux cents euros");
		assertThat(words("1000")).isEqualTo("mille euros");
		assertThat(words("200000")).isEqualTo("deux cent mille euros");
		assertThat(words("80000")).isEqualTo("quatre-vingt mille euros");
		assertThat(words("509000")).isEqualTo("cinq cent neuf mille euros");
		assertThat(words("1250000.50")).isEqualTo("un million deux cent cinquante mille euros et cinquante centimes");
		assertThat(words("0.01")).isEqualTo("zéro euro et un centime");
	}

}
