package game;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.*;

import qlearn.Q_learn;
 
public class DrawGraphFloat extends JPanel {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	List<Float> v = new ArrayList<Float>();
	private BufferedImage paintImage = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_3BYTE_BGR);
	private String name;
    final int PAD = 20;
	private static final int WIDTH = 1280, HEIGHT = 720;
 
    public DrawGraphFloat(List<Float> vector, String n){
    	v = vector;
    	name=n;
    }
    
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = paintImage.createGraphics(); 
        //Graphics2D g2 = (Graphics2D)g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        // Draw ordinate.
        g2.draw(new Line2D.Double(PAD, PAD, PAD, h-PAD));
        // Draw abcissa.
        g2.draw(new Line2D.Double(PAD, h-PAD, w-PAD, h-PAD));
        double xInc = (double)(w - 2*PAD)/(v.size()-1);
        double scale = (double)(h - 2*PAD)/getMax(v);
        
        g2.drawString("Max: " + String.valueOf(getMax(v)), PAD, PAD/2);
        g2.drawString("Average: " + String.valueOf(getAvg(v)), PAD + 150, PAD/2);
        
        // Mark data points.
        g2.setPaint(Color.red);
        for(int i = 0; i < v.size(); i++) {
            double x = PAD + i*xInc;
            double y = h - PAD - scale*v.get(i);
            
            
            g2.fill(new Ellipse2D.Double(x-2, y-2, 4, 4));
            
        }
        
        for(int i = 0; i < v.size()-1; i++) {
            double x = PAD + i*xInc;
            double y = h - PAD - scale*v.get(i);
            
            double x2 = PAD + (i+1)*xInc;
            double y2 = h - PAD - scale*v.get(i+1);
            
            g2.draw(new Line2D.Double(x,y,x2,y2));
        }
        
        g.drawImage(paintImage, 0, 0, null);
        try {
			save();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
 
    private float getMax(List<Float> v) {
        float max = -Float.MAX_VALUE;
        for(int i = 0; i < v.size(); i++) {
            if(v.get(i) > max)
                max = v.get(i);
        }
        return max;
    }
    
    private float getAvg(List<Float> v) {
        float avg = 0;
        for(int i = 0; i < v.size(); i++) {
            avg += v.get(i);
        }
        return avg/v.size();
    }
 
    public static void draw(List<Float> v, String s) {
        JFrame f = new JFrame();
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.add(new DrawGraphFloat(v,s));
        f.setSize(WIDTH,HEIGHT);
        f.setTitle(s);
        f.setLocation(200,200);
        f.setVisible(true);
    }
    
    
    public void save() throws IOException{
        ImageIO.write(paintImage, "PNG", new File(name + ".png"));
    }
    
    /*
    public void save(Graphics2D g2d)
    {
    	BufferedImage img = null;
    	g2d.drawImage(img, null, 0, 0);
    	try{
    		ImageIO.write(img, "JPEG", new File("foo.jpg"));
    	}
    	catch(IOException e){
            e.printStackTrace();
    	}
    }*/
}
