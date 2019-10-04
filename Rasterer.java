import java.awt.geom.Point2D;
import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class provides all code necessary to take a query box and produce
 * a query result. The getMapRaster method must return a Map containing all
 * seven of the required fields, otherwise the front end code will probably
 * not draw the output correctly.
 */
public class Rasterer {

    public Rasterer() {
        // YOUR CODE HERE

    }

    /**
     * Takes a user query and finds the grid of images that best matches the query. These
     * images will be combined into one big image (rastered) by the front end. <br>
     *
     *     The grid of images must obey the following properties, where image in the
     *     grid is referred to as a "tile".
     *     <ul>
     *         <li>The tiles collected must cover the most longitudinal distance per pixel
     *         (LonDPP) possible, while still covering less than or equal to the amount of
     *         longitudinal distance per pixel in the query box for the user viewport size. </li>
     *         <li>Contains all tiles that intersect the query bounding box that fulfill the
     *         above condition.</li>
     *         <li>The tiles must be arranged in-order to reconstruct the full image.</li>
     *     </ul>
     *
     * @param params Map of the HTTP GET request's query parameters - the query box and
     *               the user viewport width and height.
     *
     * @return A map of results for the front end as specified: <br>
     * "render_grid"   : String[][], the files to display. <br>
     * "raster_ul_lon" : Number, the bounding upper left longitude of the rastered image. <br>
     * "raster_ul_lat" : Number, the bounding upper left latitude of the rastered image. <br>
     * "raster_lr_lon" : Number, the bounding lower right longitude of the rastered image. <br>
     * "raster_lr_lat" : Number, the bounding lower right latitude of the rastered image. <br>
     * "depth"         : Number, the depth of the nodes of the rastered image;
     *                    can also be interpreted as the length of the numbers in the image
     *                    string. <br>
     * "query_success" : Boolean, whether the query was able to successfully complete; don't
     *                    forget to set this to true on success! <br>
     */
    private File[] getFilesOfDepth(File dir, int depth) {
        return dir.listFiles(file -> file.getName().startsWith("d"+depth));
    }

    public static boolean axisCollidesOrContains(double min0, double max0, double min1, double max1) {
        return !(max0 < min1 || max1 < min0);
    }

    public static boolean photoIsUseful(double minX, double minY, double maxX, double maxY, double minBoxX, double minBoxY, double maxBoxX, double maxBoxY) {
        return axisCollidesOrContains(minX, maxX, minBoxX, maxBoxX) &&
                axisCollidesOrContains(minY, maxY, minBoxY, maxBoxY);
    }

    public static Point2D getXY(String name) {
        String elements[] = name.substring(0, name.lastIndexOf(".")).split("_");
        int x = Integer.parseInt(elements[1].substring(1)),
                y = Integer.parseInt(elements[2].substring(1));
        return new Point2D.Float(x, y);
    }

    public Map<String, Object> getMapRaster(Map<String, Double> params) {
        System.out.println(params);
        Map<String, Object> results = new HashMap<>();
        double lonDPPquerybox = (params.get("lrlon") - params.get("ullon"))/params.get("w");
        double lonDPPdepths[] = {0.00034332275390625,0.000171661376953125,0.0000858306884765625,0.00004291534423828125,0.000021457672119140625,0.000010728836059570312,0.000005364418029785156,0.000002682209014892578};
        double latDPPdepths[] = {0.000271066850456234375,0.000135533425228109375,0.000067766712614046875,0.000033883356307015625,0.0000169416781535,0.0000084708390767421875,0.0000042354195383671875,0.00000211770976918359375};
        int choosenDepth = 0;
        boolean check = true;



        if(lonDPPquerybox > lonDPPdepths[0]) {
            choosenDepth = 0;
            check = false;
        }
        if(check) {
            for (int i = 0; i < 7; i++) {
                if (lonDPPquerybox == lonDPPdepths[i]) {
                    choosenDepth = i;
                } else if (lonDPPquerybox < lonDPPdepths[i]) {
                    if (lonDPPquerybox >= lonDPPdepths[i + 1]) {
                        choosenDepth = i + 1;
                        break;
                    }
                    if(i==6) {
                        choosenDepth = 7;
                    }
                }
            }
        }

        File files[] = getFilesOfDepth(new File("../library-sp18/data/proj3_imgs"),choosenDepth);
        double widthOfFilesInLongitude = 256*lonDPPdepths[choosenDepth];
        double heightOfFilesInLatitude = 256*latDPPdepths[choosenDepth];

        int timesToMulLon = 0;
        int timesToMulLat = 0;
        double ullon_file;
        double lrlon_file;
        double ullat_file;
        double lrlat_file;
        System.out.println((int) Math.pow(4,choosenDepth));
        String renderGridOneDim[] = new String[(int) Math.pow(4,choosenDepth)];
        int i=0;
        for(File file : files) {
            timesToMulLon = Integer.parseInt(file.getName().substring(file.getName().indexOf("x") + 1, file.getName().indexOf("_", file.getName().indexOf("x") + 1)));

            timesToMulLat = Integer.parseInt(file.getName().substring(file.getName().indexOf("y") + 1, file.getName().indexOf(".")));

            ullon_file = MapServer.ROOT_ULLON + (timesToMulLon * widthOfFilesInLongitude);

            lrlon_file = MapServer.ROOT_ULLON + ((timesToMulLon + 1) * widthOfFilesInLongitude);

            ullat_file = MapServer.ROOT_ULLAT - (timesToMulLat * heightOfFilesInLatitude);

            lrlat_file = MapServer.ROOT_ULLAT - ((timesToMulLat + 1) * heightOfFilesInLatitude);

            if(photoIsUseful(ullon_file,-ullat_file,lrlon_file,-lrlat_file,params.get("ullon"),-params.get("ullat"),params.get("lrlon"),-params.get("lrlat"))) {
                renderGridOneDim[i++] = file.getName();
            }

        }
        if(i==0){
            results.put("query_success",false);
        }else{
            results.put("query_success",true);
        }

        double raster_ul_lon=0;
        double raster_ul_lat=0;
        double raster_lr_lon=0;
        double raster_lr_lat=0;


        int minX=Integer.MAX_VALUE, maxX=Integer.MIN_VALUE, minY=Integer.MAX_VALUE, maxY=Integer.MIN_VALUE;

        for(String name: renderGridOneDim)
        {
            if(name==null) {
                continue;
            }
            Point2D xy = getXY(name);

            if(minX > xy.getX()) {
                minX = (int) xy.getX();
            }
            if(maxX < xy.getX()) {
                maxX = (int) xy.getX();
            }

            if(minY > xy.getY()) {
                minY = (int) xy.getY();
            }
            if(maxY < xy.getY()) {
                maxY = (int)xy.getY();
            }
        }
        int diffX = maxX - minX +1;
        int diffY = maxY - minY +1;

        double raster_lon[] = new double[2];
        double raster_lat[] = new double[2];
        int xs[] = new int[]{minX,maxX};
        int ys[] = new int[]{minY,maxY};
        for(int index=0;index<2;index++) {
            timesToMulLon = xs[index];

            timesToMulLat = ys[index];

            ullon_file = MapServer.ROOT_ULLON + (timesToMulLon * widthOfFilesInLongitude);
            lrlon_file = MapServer.ROOT_ULLON + ((timesToMulLon + 1) * widthOfFilesInLongitude);
            raster_lon[index] = index==0 ? ullon_file : lrlon_file;


            ullat_file = MapServer.ROOT_ULLAT - (timesToMulLat * heightOfFilesInLatitude);
            lrlat_file = MapServer.ROOT_ULLAT - ((timesToMulLat + 1) * heightOfFilesInLatitude);
            raster_lat[index] = index==0 ? ullat_file : lrlat_file;
        }

        results.put("raster_ul_lon",raster_lon[0]);
        results.put("raster_ul_lat",raster_lat[0]);
        results.put("raster_lr_lon",raster_lon[1]);
        results.put("raster_lr_lat",raster_lat[1]);

        String renderGrid[][] = new String[diffY][diffX];
        for(String name: renderGridOneDim) {
            if(name==null) {
                continue;
            }
            Point2D xy = getXY(name);
            renderGrid[(int)xy.getY() - minY][(int)xy.getX() - minX] = name;
        }

        results.put("render_grid",renderGrid);
        results.put("depth",choosenDepth);

        return results;
    }

}
