package Week3;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NameScoreTest {


//This will not work for array type input
//    @ParameterizedTest
//    @CsvSource({
//            "'ATA',             22",
//            "'',                0",
//            ",                 0",
//            "'AtA',             0",
//            "'ATA','BURAK',     75",
//    })
//    void totalScore(ArrayList<String> names, int totalScore) {
//        //assertEquals(totalScore, NameScore.totalScore(names));
//        //assertEquals(totalScore, NameScoreComplete.totalScore(names));
//    }

    @ParameterizedTest
    @CsvSource({
            "'ATA',             22",
            "'',                0",
            " ,                 0",
            "'AtA',             0",
            "'ATA,BURAK',     75",
    })
    void totalScoreWithArrayInput(@ConvertWith(StringArrayConverter.class) ArrayList<String> names, int totalScore) {
        assertEquals(totalScore, NameScoreComplete.totalScore(names));
        //assertEquals(totalScore, NameScore.totalScore(names));
    }

}