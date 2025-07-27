package Backend.Arm.tools;

public class RegisterIdAllocator {
    private int id;

    public RegisterIdAllocator(){
        id = 0;
    }

    public int getId(){
        return id++;
    }
}
