package com.mappy.fpm.batches.tomtom.dbf.geocodes;

import com.google.common.base.Enums;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.mappy.fpm.batches.tomtom.TomtomFolder;
import com.mappy.fpm.batches.tomtom.dbf.names.AlternativeName;
import com.mappy.fpm.batches.tomtom.dbf.names.Language;
import lombok.extern.slf4j.Slf4j;
import org.jamel.dbf.DbfReader;
import org.jamel.dbf.structure.DbfRow;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

@Slf4j
@Singleton
public class GeocodeProvider {

    private final Map<Long, List<Geocode>> alternateNames = newHashMap();
    private final TomtomFolder folder;

    @Inject
    public GeocodeProvider(TomtomFolder folder) {
        this.folder = folder;
    }

    public void loadGeocodingAttributes(String filename) {
        alternateNames.putAll(readFile(filename));
    }

    public Map<String, String> getAlternateRoadNamesWithSide(Long tomtomId) {
        return ofNullable(alternateNames.get(tomtomId))
                .orElse(ImmutableList.of())
                .stream()
                .filter(alternativeName -> ofNullable(alternativeName.getSideOfLine()).isPresent())
                .collect(Collectors.toMap(this::getKeyAlternativeNameWithSide, Geocode::getName, mergeIntoMap()));
    }

    private Optional<String> getSideOfLine(Long side) {
        if (side == 1) {
            return of("left");
        } else if (side == 2) {
            return of("right");
        }
        return empty();
    }

    private String getKeyAlternativeNameWithSide(Geocode geocode) {
        Optional<String> side = getSideOfLine(geocode.getSideOfLine());
        Optional<Language> language = ofNullable(Enums.getIfPresent(Language.class, geocode.getLanguage()).orNull());
        return "name" + language.map(language1 -> ":" + side.map(s -> s + ":").orElse("") + language1.getValue()).orElse(side.map(s -> ":" + s).orElse(""));
    }

    private BinaryOperator<String> mergeIntoMap() {
        return (key1, key2) -> key2;
    }

    private Map<Long, List<Geocode>> readFile(String filename) {
        Map<Long, List<Geocode>> geocodes = newHashMap();

        File file = new File(folder.getFile(filename));
        if (file.exists()) {
            log.info("Reading {}", file);
            try (DbfReader reader = new DbfReader(file)) {
                DbfRow row;
                Stopwatch stopwatch = Stopwatch.createStarted();
                int counter = 0;

                while ((row = reader.nextRow()) != null) {
                    Geocode geocode = Geocode.fromDbf(row);
                    List<Geocode> geocodingAttributes = geocodes.containsKey(geocode.getId()) ? geocodes.get(geocode.getId()) : newArrayList();
                    geocodingAttributes.add(geocode);
                    geocodes.put(geocode.getId(), geocodingAttributes);
                    counter++;
                }
                long time = stopwatch.elapsed(MILLISECONDS);
                stopwatch.stop();
                log.info("Added {} object(s){}", counter, counter > 0 ? " in " + time + " ms at rate " + String.format("%.2f", counter * 1.0 / time) + " obj/ms" : "");
            }

        } else {
            log.info("File not found : {}", file.getAbsolutePath());
        }

        return geocodes;
    }
}
