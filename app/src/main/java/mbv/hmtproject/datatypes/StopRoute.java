package mbv.hmtproject.datatypes;


public class StopRoute {
    public int Id;
    public String VehicleType;
    public String Number;
    public String EndStop;
    public String Nearest;
    public String Next;

    public String getFullNumber() {
        return VehicleType + Number;
    }
}