package net.teamfruit.eewbot.dispatcher;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import net.teamfruit.eewbot.dispatcher.QuakeInfoDispather.QuakeInfo.PrefectureDetail.SeismicIntensity;

public class QuakeInfoDispather implements Runnable {

	public static final String REMOTE = "https://typhoon.yahoo.co.jp/weather/earthquake/";

	@Override
	public void run() {
		//TODO
	}

	public static QuakeInfo get() throws IOException {
		return new QuakeInfo(Jsoup.connect(REMOTE).get());
	}

	public static class QuakeInfo {
		public static final SimpleDateFormat FORMAT = new SimpleDateFormat("yyyy年M月d日 H時mm分");

		private final String imageUrl;
		private final Date announceTime;
		private final Date quakeTime;
		private final String epicenter;
		private final String lat;
		private final String lon;
		private final String depth;
		private final float magnitude;
		private final String info;
		private final Set<PrefectureDetail> details = new TreeSet<>(Comparator.reverseOrder());

		public QuakeInfo(final Document doc) {
			this.imageUrl = doc.getElementById("yjw_keihou").getElementsByTag("img").first().attr("src");
			final Element info = doc.getElementById("eqinfdtl");
			final Map<String, String> data = info.getElementsByTag("table").get(0).getElementsByTag("tr").stream()
					.map(tr -> tr.getElementsByTag("td")).collect(Collectors.toMap(td -> td.get(0).text(), td -> td.get(1).text()));

			try {
				this.announceTime = FORMAT.parse(data.get("情報発表時刻"));
				final String quakeTime = data.get("発生時刻");
				this.quakeTime = FORMAT.parse(StringUtils.substring(quakeTime, 0, quakeTime.length()-2));
				this.epicenter = data.get("震源地");
				this.lat = data.get("緯度");
				this.lon = data.get("経度");
				this.depth = data.get("深さ");
				this.magnitude = Float.parseFloat(data.get("マグニチュード"));
				this.info = data.get("情報");
			} catch (final ParseException e) {
				throw new RuntimeException("Parse Error", e);
			}

			info.getElementsByTag("table").get(1).getElementsByTag("tr").forEach(tr -> {
				final SeismicIntensity intensity = SeismicIntensity.get(tr.getElementsByTag("td").first().text());
				final Elements td = tr.getElementsByTag("table").first().getElementsByTag("td");
				final String prefecture = td.get(0).text();
				final PrefectureDetail detail = this.details.stream().filter(line -> line.getPrefecture().equals(prefecture)).findAny()
						.orElseGet(() -> new PrefectureDetail(prefecture));
				this.details.add(detail);
				detail.addCity(intensity, prefecture);
			});
		}

		public static class PrefectureDetail implements Comparable<PrefectureDetail> {

			private final String prefecture;
			private final Map<SeismicIntensity, List<String>> cities = new TreeMap<>(Comparator.reverseOrder());

			public PrefectureDetail(final String prefecture) {
				this.prefecture = prefecture;
			}

			public String getPrefecture() {
				return this.prefecture;
			}

			public Map<SeismicIntensity, List<String>> getCities() {
				return this.cities;
			}

			public void addCity(final SeismicIntensity intensity, final String city) {
				this.cities.computeIfAbsent(intensity, key -> new ArrayList<>()).add(city);
			}

			public Optional<SeismicIntensity> getMaxIntensity() {
				if (this.cities.size()>0)
					return Optional.of(this.cities.keySet().iterator().next());
				return Optional.empty();
			}

			@Override
			public int compareTo(final PrefectureDetail o) {
				return getMaxIntensity().map(intensity -> intensity.compareTo(o.getMaxIntensity().orElseThrow(() -> new IllegalArgumentException())))
						.orElseThrow(() -> new IllegalStateException());
			};

			@Override
			public int hashCode() {
				final int prime = 31;
				int result = 1;
				result = prime*result+((this.prefecture==null) ? 0 : this.prefecture.hashCode());
				return result;
			}

			@Override
			public boolean equals(final Object obj) {
				if (this==obj)
					return true;
				if (obj==null)
					return false;
				if (!(obj instanceof PrefectureDetail))
					return false;
				final PrefectureDetail other = (PrefectureDetail) obj;
				if (this.prefecture==null) {
					if (other.prefecture!=null)
						return false;
				} else if (!this.prefecture.equals(other.prefecture))
					return false;
				return true;
			}

			public static enum SeismicIntensity {
				ONE("震度1"),
				TWO("震度2"),
				THREE("震度3"),
				FOUR("震度4"),
				FIVE_MINUS("震度5弱"),
				FIVE_PLUS("震度5強"),
				SIX_MINUS("震度6弱"),
				SIX_PLUS("震度6強"),
				SEVEN("震度7");

				private final String name;

				private SeismicIntensity(final String name) {
					this.name = name;
				}

				@Override
				public String toString() {
					return this.name;
				}

				public static SeismicIntensity get(final String name) {
					return Stream.of(values()).filter(value -> value.toString().equals(name)).findAny().orElse(null);
				}
			}
		}
	}
}