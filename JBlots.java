import java.awt.Dialog;
import java.awt.Panel;
import java.awt.Image;
import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Label;
import java.awt.Choice;
import java.awt.Scrollbar;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Insets;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.AWTEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentListener;
import java.awt.event.WindowListener;
import java.awt.event.ItemListener;
import java.awt.event.FocusListener;
import java.awt.event.MouseListener;
import java.awt.event.ActionEvent;
import java.awt.event.AdjustmentEvent;
import java.awt.event.WindowEvent;
import java.awt.event.ItemEvent;
import java.awt.event.FocusEvent;
import java.awt.event.MouseEvent;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.Undo;
import ij.WindowManager;
import ij.CompositeImage;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.process.ByteProcessor;
import ij.gui.ImageCanvas;
import ij.gui.GUI;
import ij.gui.TrimmedButton;
import ij.gui.Roi;
import ij.gui.PolygonRoi;
import ij.gui.Wand;
import ij.gui.Overlay;
import ij.gui.ImageWindow;
import ij.measure.Measurements;
import ij.plugin.frame.PasteController;
import ij.plugin.filter.Analyzer;
import ij.plugin.filter.BackgroundSubtracter;


/** Adjusts the lower and upper threshold levels of the active image. This
    class is multi-threaded to provide a more responsive user interface. */
public class JBlots extends java.awt.Dialog
    implements PlugIn, Runnable, ActionListener, AdjustmentListener, WindowListener, ItemListener, FocusListener, MouseListener {

    public static final String LOC_KEY = "threshold.loc";
    public static final String MODE_KEY = "threshold.mode";
    public static final String DARK_BACKGROUND = "threshold.dark";
    static final int RED=0, BLACK_AND_WHITE=1, OVER_UNDER=2;
    static JBlots instance;
    static int mode = RED;
    Thread thread;

    BackgroundSubtracter bgs;
    ByteProcessor bgsImage;

    double ballRadius = 20.0;
    int thresholdValue = 20;

    boolean doAutoAdjust, doMeasure, doSet, doImageClick, doAdjustBallRadius, doAdjustThreshold, doBackgroundChanged, doShowProcessed;

    Button autoB, measureB, setB;
    int previousImageID;
    int previousImageType;
    double previousMin, previousMax;
    int previousSlice;
    Scrollbar ballRadiusSlider;
    Scrollbar thresholdSlider;
    Label label1, label2;
    Checkbox darkBackground;
    Checkbox showProcessed;
    boolean firstActivation;

    ImageCanvas canvas = null;
    int imageX = -1, imageY = -1;

    ImageWindow processedImage = null;

    // a signal to exit the compute thread
    boolean done = false;

    public JBlots() {
        super(IJ.isJava16()?null:new Frame(), "JBlots");
        enableEvents(AWTEvent.WINDOW_EVENT_MASK);
        final ImageJ ij = IJ.getInstance();
        addWindowListener(this);
        addFocusListener(this);
        if (IJ.isLinux()) setBackground(ImageJ.backgroundColor);
        if (ij!=null && !IJ.isMacOSX() && IJ.isJava16()) {
            Image img = ij.getIconImage();
            if (img!=null)
                try {setIconImage(img);} catch (Exception e) {}
        }

        if (instance!=null) {
            instance.firstActivation = true;
            instance.toFront();
            return;
        }
        instance = this;

        this.bgs = new BackgroundSubtracter();

        WindowManager.addWindow(this);
        IJ.register(PasteController.class);

        final Font font = new Font("SansSerif", Font.PLAIN, 10);
        final GridBagLayout gridbag = new GridBagLayout();
        final GridBagConstraints c = new GridBagConstraints();
        setLayout(gridbag);

        // plot
        int y = 0;
        c.gridx = 0;
        c.gridy = y++;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.BOTH;
        c.anchor = GridBagConstraints.CENTER;
        c.insets = new Insets(10, 10, 0, 10);

        // Ball Radius slider for the background subtraction
        ballRadiusSlider = new Scrollbar(Scrollbar.HORIZONTAL, 200, 1, 0, 1000);
        GUI.fix(ballRadiusSlider);
        c.gridx = 0;
        c.gridy = y++;
        c.gridwidth = 1;
        c.weightx = IJ.isMacintosh()?90:100;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(5, 10, 0, 0);
        add(ballRadiusSlider, c);
        ballRadiusSlider.addAdjustmentListener(this);
        ballRadiusSlider.addKeyListener(ij);
        ballRadiusSlider.setUnitIncrement(1);
        ballRadiusSlider.setFocusable(false);

        // Ball Radius slider label
        c.gridx = 1;
        c.gridwidth = 1;
        c.weightx = IJ.isMacintosh()?10:0;
        c.insets = new Insets(5, 0, 0, 10);
        label1 = new Label("       ", Label.RIGHT);
    	label1.setFont(font);
        //label1.setText("" + ballRadiusSlider.getValue());
        add(label1, c);

        // Threshold slider
        thresholdSlider = new Scrollbar(Scrollbar.HORIZONTAL, 20, 1, 0, 256);
        GUI.fix(thresholdSlider);
        c.gridx = 0;
        c.gridy = y++;
        c.gridwidth = 1;
        c.weightx = 100;
        c.insets = new Insets(2, 10, 0, 0);
        add(thresholdSlider, c);
        thresholdSlider.addAdjustmentListener(this);
        thresholdSlider.addKeyListener(ij);
        thresholdSlider.setUnitIncrement(1);
        thresholdSlider.setFocusable(false);

        // Threshold slider label
        c.gridx = 1;
        c.gridwidth = 1;
        c.weightx = 0;
        c.insets = new Insets(2, 0, 0, 10);
        label2 = new Label("       ", Label.RIGHT);
    	label2.setFont(font);
        //label2.setText("" + thresholdSlider.getValue());
        add(label2, c);
        readSliders();

        // checkboxes
        Panel panel = new Panel();
        boolean db = Prefs.get(DARK_BACKGROUND, Prefs.blackBackground?true:false);
        darkBackground = new Checkbox("Dark background");
        darkBackground.setState(db);
        darkBackground.addItemListener(this);
        panel.add(darkBackground);

        showProcessed = new Checkbox("Show processed");
        showProcessed.setState(false);
        showProcessed.addItemListener(this);
        panel.add(showProcessed);
        c.gridx = 0;
        c.gridy = y++;
        c.gridwidth = 2;
        c.insets = new Insets(5, 5, 0, 5);
        add(panel, c);

        // buttons
        int trim = IJ.isMacOSX()?11:0;
        panel = new Panel();
        autoB = new TrimmedButton("Auto",trim);
        autoB.addActionListener(this);
        autoB.addKeyListener(ij);
        panel.add(autoB);
        measureB = new TrimmedButton("Measure",trim);
        measureB.addActionListener(this);
        measureB.addKeyListener(ij);
        panel.add(measureB);
        setB = new TrimmedButton("Set",trim);
        setB.addActionListener(this);
        setB.addKeyListener(ij);
        panel.add(setB);
        c.gridx = 0;
        c.gridy = y++;
        c.gridwidth = 2;
        c.insets = new Insets(0, 5, 10, 5);
        add(panel, c);

        addKeyListener(ij);  // ImageJ handles keyboard shortcuts
        pack();
        Point loc = Prefs.getLocation(LOC_KEY);
        if (loc!=null)
            setLocation(loc);
        else
            GUI.center(this);
        setResizable(false);
        show();

        thread = new Thread(this, "JBlots");
        //thread.setPriority(thread.getPriority()-1);
        thread.start();
        final ImagePlus imp = WindowManager.getCurrentImage();
        if( imp != null ) {
            setup(imp);
            adjustBallRadius(imp, imp.getProcessor());
        }
    }


    public void run(String arg) {
        if( arg.equals("about") ) {
            IJ.showStatus("JBlots plugin");
        }
    }


    private void readSliders() {
        ballRadius = ballRadiusSlider.getValue() / 10.0;
        label1.setText("" + ballRadius);
        thresholdValue = thresholdSlider.getValue();
        label2.setText("" + thresholdValue);
    }


    public synchronized void adjustmentValueChanged(AdjustmentEvent e) {
        if( e.getSource() == ballRadiusSlider ) {
            doAdjustBallRadius = true;
        }
        else {
            doAdjustThreshold = true;
        }
        readSliders();
        notify();
    }


    public synchronized void actionPerformed(ActionEvent e) {
        final Button b = (Button)e.getSource();
        if( b == null ) return;
        else if( b == measureB )
            doMeasure = true;
        else if( b == autoB )
            doAutoAdjust = true;
        else if( b == setB )
            doSet = true;
        notify();
    }


    public synchronized void itemStateChanged(ItemEvent e) {
        final Object source = e.getSource();
        if( source == this.darkBackground ) {
            this.doBackgroundChanged = true;
        } else if( source == this.showProcessed ) {
            this.doShowProcessed = true;
        }
        notify();
    }


    ImageProcessor setup(ImagePlus imp) {
        // register for mouse clicks if necessary
        if( this.canvas == null ) {
            this.canvas = imp.getCanvas();
            if( this.canvas != null ) {
                this.canvas.addMouseListener(this);
            }
        }

        int type = imp.getType();
        if (type==ImagePlus.COLOR_RGB || (imp.isComposite()&&((CompositeImage)imp).getMode()==CompositeImage.COMPOSITE))
            return null;
        final ImageProcessor ip = imp.getProcessor();
        boolean minMaxChange = false;
        boolean not8Bits = type==ImagePlus.GRAY16 || type==ImagePlus.GRAY32;
        int slice = imp.getCurrentSlice();
        if (not8Bits) {
            if (ip.getMin()!=previousMin || ip.getMax()!=previousMax) {
                minMaxChange = true;
                previousMin = ip.getMin();
                previousMax = ip.getMax();
            } else if (slice!=previousSlice)
                minMaxChange = true;
        }
        int id = imp.getID();
        if (minMaxChange || id!=previousImageID || type!=previousImageType) {
            //IJ.log(minMaxChange +"  "+ (id!=previousImageID)+"  "+(type!=previousImageType));
            Undo.reset();
            imp.updateAndDraw();
        }
        previousImageID = id;
        previousImageType = type;
        previousSlice = slice;
        return ip;
    }


    boolean isLevelWithinThreshold(int level) {
        final boolean darkBackground = this.darkBackground.getState();
        final int threshold = this.thresholdValue;
        return darkBackground ? (level >= threshold) : (level <= threshold);
    }


    void adjustThreshold(ImagePlus imp, ImageProcessor ip) {
        final int x = this.imageX;
        final int y = this.imageY;

        if( ! this.showProcessed.getState() ) {
            if( x < 0 || y < 0 ) {
                IJ.showStatus("Wrong coords: x = " + x + "; y = " + y);
                return;
            }

            if( ! isLevelWithinThreshold( ip.get(x, y) ) ) {
                IJ.showStatus("Image level " + ip.get(x, y) + " is not in the blot at threshold " + this.thresholdValue);
                // Remove the blot overlay
                imp.killRoi();
                return;
            }
        }

        // Compute a thresholded B&W image
        final int width = ip.getWidth();
        final int height = ip.getHeight();
        ImageProcessor ip2 = new ByteProcessor(width, height);
        for( int i = 0; i < width; i++ )
            for( int j = 0; j < height; j++ ) {
                final int p = isLevelWithinThreshold( bgsImage.get(i,j) ) ? 255 : 0;
                ip2.putPixel(i, j, p);
            }

        if( this.showProcessed.getState() ) {
            // create the ImageWindow if necessary
            if( this.processedImage == null ) {
                ImagePlus imp2 = new ImagePlus("Processed Image", ip2);
                this.processedImage = new ImageWindow(imp2);
                imp2.show();
                IJ.showStatus("Created processed image window");
            }
            else {
                ImagePlus imp2 = this.processedImage.getImagePlus();
                imp2.setProcessor(ip2);
            }
        }
        else {
            // check if the processed image window exists and close it
            if( this.processedImage != null ) {
                this.processedImage.close();
                this.processedImage = null;
                IJ.showStatus("Closed processed image window");
            }
        }

        // draw the blot outline if the current coords are within one
        if( x >= 0 && y >= 0 && isLevelWithinThreshold( ip.get(x,y) ) ) {
            final Wand wand = new Wand(ip2);
            wand.autoOutline(x, y);
            final PolygonRoi roi = new PolygonRoi(wand.xpoints, wand.ypoints, wand.npoints, Roi.FREEROI);
            imp.setRoi(roi);
        }
        else {
            imp.killRoi();
        }

    }


    void adjustBallRadius(ImagePlus imp, ImageProcessor ip) {
        this.bgsImage = new ByteProcessor(ip, false);
        final boolean lightBackground = ! darkBackground.getState();
        bgs.rollingBallBackground(this.bgsImage, this.ballRadius, false, lightBackground, false, false, false);
        adjustThreshold(imp, ip);
    }


    // Separate thread that does the potentially time-consuming processing
    public void run() {
        final int MEASURE = 0, AUTO = 1, SET = 2, BALL_RADIUS = 3, STATE_CHANGE = 4, THRESHOLD = 5, IMAGE_CLICK = 7, BACKGROUND_CHANGED = 8, SHOW_PROCESSED = 9;

        while( !this.done ) {
            synchronized( this ) {
                try { wait(); }
                catch(InterruptedException e) {}
            }

            int action;
            if (doMeasure) action = MEASURE;
            else if (doAutoAdjust) action = AUTO;
            else if (doSet) action = SET;
            else if (doImageClick) action = IMAGE_CLICK;
            else if (doAdjustThreshold) action = THRESHOLD;
            else if (doAdjustBallRadius) action = BALL_RADIUS;
            else if (doBackgroundChanged) action = BACKGROUND_CHANGED;
            else if (doShowProcessed) action = SHOW_PROCESSED;
            else continue;

            doAutoAdjust = doMeasure = doSet = doImageClick = doAdjustBallRadius = doAdjustThreshold = doBackgroundChanged = doShowProcessed = false;

            final ImagePlus imp = WindowManager.getCurrentImage();
            if( imp == null ) {
                IJ.beep();
                IJ.showStatus("No image");
                continue;
            }

            final ImageProcessor ip = setup(imp);
            if( ip == null ) {
                imp.unlock();
                IJ.beep();
                if (imp.isComposite())
                    IJ.showStatus("\"Composite\" mode images cannot be thresholded");
                else
                    IJ.showStatus("RGB images cannot be thresholded");
                continue;
            }

            switch( action ) {
            case IMAGE_CLICK:
                adjustThreshold(imp, ip);
                break;
            case THRESHOLD:
                adjustThreshold(imp, ip);
                break;
            case BALL_RADIUS:
            case BACKGROUND_CHANGED:
            case SHOW_PROCESSED:
                adjustBallRadius(imp, ip);
                break;
            case MEASURE:
                Analyzer.setMeasurements(Measurements.AREA + Measurements.MEAN + Measurements.INTEGRATED_DENSITY);
                Analyzer a = new Analyzer(imp);
                a.measure();
                a.displayResults();
                IJ.showStatus("Measurement taken");
                break;
            }
            ip.setLutAnimation(true);
            imp.updateAndDraw();
        }
    }


    /** Overrides close() in PlugInFrame. */
    public void close() {
    	//super.close();
        //setVisible(false);
        dispose();
        WindowManager.removeWindow(this);

        instance = null;
        this.done = true;
        Prefs.saveLocation(LOC_KEY, getLocation());
        Prefs.set(MODE_KEY, mode);
        Prefs.set(DARK_BACKGROUND, darkBackground.getState());
        synchronized( this ) {
            notify();
        }
    }


    public void windowActivated(WindowEvent e) {
    	//super.windowActivated(e);
        WindowManager.setWindow(this);
        ImagePlus imp = WindowManager.getCurrentImage();
        if (imp!=null && firstActivation) {
            previousImageID = 0;
            setup(imp);
            firstActivation = false;
        }
    }


    /** Notifies the JBlots that the image has changed. */
    public static void update() {
        if (instance!=null) {
            JBlots jb = instance;
            ImagePlus imp = WindowManager.getCurrentImage();
            if (imp!=null && jb.previousImageID==imp.getID()) {
                jb.previousImageID = 0;
                jb.setup(imp);
            }
        }
    }


    public void windowClosing(WindowEvent e) {
    	if (e.getSource()==this) {
            close();
    	}
    }


    public void focusGained(FocusEvent e) {
        WindowManager.setWindow(this);
    }


    public void windowOpened(WindowEvent e) {}
    public void windowClosed(WindowEvent e) {}
    public void windowIconified(WindowEvent e) {}
    public void windowDeiconified(WindowEvent e) {}
    public void windowDeactivated(WindowEvent e) {}
    public void focusLost(FocusEvent e) {}


    public synchronized void mouseClicked(MouseEvent e) {
        this.canvas = (ImageCanvas)e.getComponent();
        final Point loc = this.canvas.getCursorLoc();
        this.imageX = (int)loc.getX();
        this.imageY = (int)loc.getY();
        doImageClick = true;
        notify();
    }


    public void mouseEntered(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {}
    public void mousePressed(MouseEvent e) {}
    public void mouseReleased(MouseEvent e) {}

} // JBlots
