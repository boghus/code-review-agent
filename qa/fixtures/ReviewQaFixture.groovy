package qa.fixtures

import java.io.File

class ReviewQaFixture {

    static String loadName(Map<String, String> input) {
        String name = input.get('name')
        name?.toUpperCase()
    }

    static String execute(String command) {
        Runtime.runtime.exec(command)
    }

    static String readConfig(File file) {
        FileInputStream stream = new FileInputStream(file)
        stream.text
    }
}
