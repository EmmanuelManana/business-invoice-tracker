package com.invoicetracker.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReminderScheduleTest {

    private static final LocalDate DUE = LocalDate.of(2026, 6, 15);
    private final ReminderSchedule schedule = new ReminderSchedule(List.of(14, 0, -3, 7, 1));

    @Test
    void stagesAreSortedAndDeduplicated() {
        var s = new ReminderSchedule(List.of(7, 1, 7, -3));
        assertThat(s.stages()).extracting(ReminderStage::offsetDays).containsExactly(-3, 1, 7);
        assertThat(s.leadDays()).isEqualTo(3);
    }

    @ParameterizedTest(name = "{0} days from due -> {1}")
    @CsvSource({
        "-10, NONE",
        "-4, NONE",
        "-3, D-3",
        "-1, D-3",
        "0, D0",
        "1, D+1",
        "6, D+1",
        "7, D+7",
        "13, D+7",
        "14, D+14",
        "200, D+14"
    })
    void latestDueStageIsTheMostRecentStageReached(int daysFromDue, String expected) {
        var stage = schedule.latestDueStage(DUE, DUE.plusDays(daysFromDue));
        assertThat(stage.map(ReminderStage::code).orElse("NONE")).isEqualTo(expected);
    }

    @Test
    void stageCodesAndLabels() {
        assertThat(new ReminderStage(-3).code()).isEqualTo("D-3");
        assertThat(new ReminderStage(0).code()).isEqualTo("D0");
        assertThat(new ReminderStage(7).code()).isEqualTo("D+7");
        assertThat(new ReminderStage(-1).label()).isEqualTo("1 day before due");
        assertThat(new ReminderStage(0).label()).isEqualTo("Due today");
        assertThat(new ReminderStage(14).label()).isEqualTo("14 days overdue");
    }

    @Test
    void emptyScheduleIsRejected() {
        assertThatThrownBy(() -> new ReminderSchedule(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
