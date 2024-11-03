package territory_bro;

import io.nayuki.qrcodegen.QrCode;

import java.util.Objects;

public class QrCodeGenerator {

    // Adapted from https://github.com/nayuki/QR-Code-generator/blob/master/java/QrCodeGeneratorDemo.java

    /**
     * Returns a string of SVG code for an image depicting the specified QR Code, with the specified
     * number of border modules. The string always uses Unix newlines (\n), regardless of the platform.
     *
     * @param qr         the QR Code to render (not {@code null})
     * @param border     the number of border modules to add, which must be non-negative
     * @param lightColor the color to use for light modules, in any format supported by CSS, not {@code null}
     * @param darkColor  the color to use for dark modules, in any format supported by CSS, not {@code null}
     * @return a string representing the QR Code as an SVG XML document
     * @throws NullPointerException     if any object is {@code null}
     * @throws IllegalArgumentException if the border is negative
     */
    public static String toSvgString(QrCode qr, int border, String lightColor, String darkColor) {
        Objects.requireNonNull(qr);
        Objects.requireNonNull(lightColor);
        Objects.requireNonNull(darkColor);
        if (border < 0) {
            throw new IllegalArgumentException("Border must be non-negative");
        }
        StringBuilder sb = new StringBuilder()
//                .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
//                .append("<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n")
                .append(String.format("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %1$d %1$d\" stroke=\"none\">\n",
                        qr.size + border * 2))
                .append("\t<rect width=\"100%\" height=\"100%\" fill=\"").append(lightColor).append("\" shape-rendering=\"crispEdges\"/>\n")
                .append("\t<path d=\"");
        for (int y = 0; y < qr.size; y++) {
            for (int x = 0; x < qr.size; x++) {
                if (qr.getModule(x, y)) {
                    if (!(x == 0 && y == 0)) {
                        sb.append(" ");
                    }
                    sb.append(String.format("M%d,%dh1v1h-1z", x + border, y + border));
                }
            }
        }
        return sb.append("\" fill=\"").append(darkColor).append("\" shape-rendering=\"crispEdges\"/>\n")
                .append("</svg>\n")
                .toString();
    }
}
