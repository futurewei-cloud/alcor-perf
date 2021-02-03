import java.lang.*;
import java.util.ArrayList;

public class DataBuffer
{
        public DataBuffer()
        {}

        public void addRow(ArrayList<String> row)
        {
            dataBuffer.add(row);
        }
        public ArrayList<ArrayList<String>> dataBuffer = new ArrayList<>();
}
