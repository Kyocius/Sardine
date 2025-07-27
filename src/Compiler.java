import Driver.Driver;

import java.io.IOException;

public class Compiler {

    public static void main(String[] args) throws IOException {
        Driver driver = new Driver();
        driver.run(args);
    }
}
