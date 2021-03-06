package sgraph;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import util.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import com.jogamp.opengl.util.texture.Texture;


/**
 * Created by yuliazileeva on 11/24/16.
 */
public class Raytracer {

    private INode root;
    private HashMap<String,String> textures;

    public Raytracer(INode root, Map<String,String> textures) {
        this.root = root;
        this.textures = (HashMap<String, String>) textures;
    }

    public void raytrace(int width, int height, Stack<Matrix4f> modelView) {
        int i, j;

        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (i = 0; i < width; i++) {
            for (j = 0; j < height; j++) {

                if (i == width / 2 && j == height / 2) {
                    System.out.println("here");
                }
                //get color in (r,g,b)
                Vector4f start = new Vector4f(0, 0, 0, 1);
                Vector4f direction = new Vector4f(i - width/2, j - height/2, (-0.5f * height) / (float) Math.tan(Math.toRadians(0.5f * 120.0f)), 0);

                Ray ray = new Ray(start, direction);

                Color color = this.raycast(ray, modelView);

                output.setRGB(i, j, color.getRGB());
            }
        }

        OutputStream outStream = null;

        AffineTransform tx = AffineTransform.getScaleInstance(1, -1);
        tx.translate(0, -output.getHeight(null));
        AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
        output = op.filter(output, null);


        try {
            outStream = new FileOutputStream("output/raytrace.png");
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException("Could not write raytraced image!");
        }

        try {
            ImageIO.write(output, "png", outStream);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not write raytraced image!");
        }
    }


    /**
     * Determines if this ray hits anything in the scene graph
     * @param ray in the view coordinate system
     * @param modelView
     */
    private Color raycast(Ray ray, Stack<Matrix4f> modelView) {
        HitRecord hr = root.intersect(ray, modelView);
        hr.setLights(root.getLights(modelView));
        Color color;
        if (hr.isHit()) {
            color = this.shade(hr);

//            String name = hr.getTextureName();
//            String path = textures.get(name);
//            String imageFormat = path.substring(path.indexOf('.')+1);
//            try {
////            fColor = fColor * texture(image,fTexCoord.st);
//                TextureImage textureImage = new TextureImage(path, imageFormat, name);
//                Texture texture = textureImage.getTexture();
//                Vector4f textureColor = textureImage.getColor(texture.getWidth(), texture.getHeight());
//
//                Vector4f colorToVector = new Vector4f((float) color.getRed() / 255, (float) color.getGreen() / 255, (float) color.getBlue() / 255, (float) color.getAlpha() / 255);
//
//                Vector4f ct = colorToVector.mul(textureColor);
//
//                color = new Color(
//                        Math.min(255, ct.x),
//                        Math.min(255, ct.y),
//                        Math.min(255, ct.z)
//                        );
//            } catch (IOException e) {
//                throw new IllegalArgumentException("Texture "+path+" cannot be read!");
//            }

//            r = g = b = 255;
        } else {
            color = new Color(0.69f, 0.8f , 0.9f);
        }

        return color;
    }

    private Vector4f reflect(Vector4f I, Vector4f N) {
        Vector4f mul = N.mul(2.0f * N.dot(I));
        Vector4f r = I.sub(mul); //I - 2.0 * dot(N, I) * N
        return r;
    }

    private Vector3f clamp(Vector3f val) {
        Vector3f clamped = new Vector3f(val);
        clamped.x = Math.min(Math.max(val.x, 0), 1);
        clamped.y = Math.min(Math.max(val.y, 0), 1);
        clamped.z = Math.min(Math.max(val.z, 0), 1);
        return clamped;
    }

    private Vector3f ambient(Material material, Light light) {

        Vector3f materialAmbient = new Vector3f(material.getAmbient().x, material.getAmbient().y, material.getAmbient().z);
        Vector3f ambient = new Vector3f(materialAmbient.mul(light.getAmbient()));
        return ambient;
    }

    private Vector3f diffuse(Material material, Light light, float nDotL) {

        Vector3f materialDiffuse = new Vector3f(material.getDiffuse().x, material.getDiffuse().y, material.getDiffuse().z);
        Vector3f diffuse = new Vector3f(materialDiffuse.mul(light.getDiffuse().mul(Math.max(nDotL, 0.0f))));
        return diffuse;
    }

    private Vector3f specular(Material material, Light light, float nDotL, float rDotV) {
        Vector3f specular;
        Vector3f materialSpecular = new Vector3f(material.getSpecular().x, material.getSpecular().y, material.getSpecular().z);

        if (nDotL > 0) {
            specular = (materialSpecular.mul(light.getSpecular())).mul((float) Math.pow(rDotV, material.getShininess()));
        } else {
            specular = new Vector3f(0, 0, 0);
        }

        return specular;
    }

    private Color shade(HitRecord hitRecord) {

        java.util.List<Light> lights = hitRecord.getLights();
        Color color = new Color(0, 0, 0);
        Material material = hitRecord.getMaterial();
        Vector4f position = hitRecord.getP();
        Vector4f normal = hitRecord.getNormal();

        for (int i = 0; i < lights.size(); i ++) {
            Light light = lights.get(i);

            Vector4f lightVec;
            if (light.getPosition().w != 0) {
                lightVec = new Vector4f(light.getPosition().sub(position));
                lightVec = lightVec.normalize();
            } else {
                lightVec = new Vector4f(light.getPosition()).negate();
                lightVec = lightVec.normalize();
            }

            Vector4f normalView = normal.normalize();
            float nDotL = normalView.dot(lightVec);

            Vector4f viewVec = position.negate();
            viewVec = viewVec.normalize();

            Vector4f reflectVec = reflect(lightVec.negate(), normalView);
            reflectVec = reflectVec.normalize();

            float rDotV = Math.max(reflectVec.dot(viewVec), 0.0f);

            Vector3f ambient = clamp(ambient(material, light));
            Vector3f diffuse = clamp(diffuse(material, light, nDotL));
            Vector3f specular = clamp(specular(material, light, nDotL, rDotV));

            float spotAngle = (float) Math.cos(Math.toRadians(light.getSpotCutoff()));
            Vector4f sd = new Vector4f(light.getSpotDirection()).normalize();
            Vector4f lightVecNeg = lightVec.negate();
            if (lightVecNeg.dot(sd) > spotAngle) {

                Vector3f ff = clamp(ambient.add(diffuse.add(specular)));

                Color newC = new Color(ff.x, ff.y, ff.z);

                int r = Math.min(255, color.getRed() + newC.getRed());
                int g = Math.min(255, color.getGreen() + newC.getGreen());
                int b = Math.min(255, color.getBlue() + newC.getBlue());

                color = new Color(r, g, b);
            }

        }
        return color;
    }
}
