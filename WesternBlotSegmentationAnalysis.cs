using UnityEngine;
using System.Collections;
using System.Collections.Generic;
using UnityEngine.UI;

#if UNITY_5_3 || UNITY_5_3_OR_NEWER
using UnityEngine.SceneManagement;
#endif
using OpenCVForUnity;

namespace OpenCVForUnityExample
{
    public class HoughLinesPExample : MonoBehaviour
    {
		//UI Controls
		[SerializeField]
		private Slider Slider01;

		//parameters
		public double threshLev = 100;
		public double thresh = 1;
		public double maxval = 255;
        void Update ()
        {
			Texture2D imgTexture = Resources.Load ("vcl_kd_WITH_LOADING_CONTROL") as Texture2D;
            		Mat imgMat = new Mat (imgTexture.height, imgTexture.width, CvType.CV_8UC3);
            		Utils.texture2DToMat (imgTexture, imgMat);
			Mat grayMat = new Mat (imgTexture.height, imgTexture.width, CvType.CV_8UC3);
            		Imgproc.cvtColor (imgMat, grayMat, Imgproc.COLOR_RGB2GRAY); //grayscale 
			Mat gray2Mat = new Mat (imgTexture.height, imgTexture.width, CvType.CV_8UC3);
			Core.bitwise_not (grayMat, gray2Mat); 	
			Mat gray3Mat = new Mat (imgTexture.height, imgTexture.width, CvType.CV_8UC3);

			Imgproc.threshold (gray2Mat, gray3Mat, thresh, maxval, Imgproc.THRESH_BINARY_INV | Imgproc.THRESH_OTSU);
			//Imgproc.threshold (gray2Mat, gray2Mat, thresh, maxval, Imgproc.THRESH_BINARY);

			Core.bitwise_not (gray3Mat, gray3Mat); 	
			Mat procMat = new Mat(imgTexture.height, imgTexture.width, CvType.CV_8UC3);
			Core.bitwise_and (gray2Mat, gray3Mat, procMat);//multiply the mask of foreground by the original image to obtain 0 background and original values in foreground
			Mat labels = new Mat (imgTexture.height, imgTexture.width, CvType.CV_8UC3);
			Mat stats = new Mat (imgTexture.height, imgTexture.width, CvType.CV_8UC3);
			Mat centroids = new Mat (imgTexture.height, imgTexture.width, CvType.CV_8UC3);
			int total = Imgproc.connectedComponentsWithStats (gray3Mat, labels, stats, centroids);

			for (int i = 1; i < total; ++i) {

				int xx = (int)centroids.get (i, 0) [0];
				int yy = (int)centroids.get (i, 1) [0];

				Imgproc.circle (procMat, new Point (xx, yy), 3, new Scalar (255, 255, 0), -1);

				int x = (int)stats.get (i, Imgproc.CC_STAT_LEFT) [0];
				int y = (int)stats.get (i, Imgproc.CC_STAT_TOP) [0];
				int height = (int)stats.get (i, Imgproc.CC_STAT_HEIGHT) [0];
				int width = (int)stats.get (i, Imgproc.CC_STAT_WIDTH) [0];
				int area = (int)stats.get (i, Imgproc.CC_STAT_AREA) [0];

				if (area > 30) {

					OpenCVForUnity.Rect rect = new OpenCVForUnity.Rect (x, y, width, height);
					Imgproc.rectangle (procMat, rect.tl (), rect.br (), new Scalar (255, 255, 0), 2);

					Mat blotMat = imgMat.submat (rect); 
					Scalar meanVa = Core.mean (blotMat);
					int meanV = (int) meanVa.val [0];
					double blotV = 0;
					Imgproc.putText (procMat, "I"+ i +"/"+ (area * meanV), new Point (x , y - 50), Core.FONT_HERSHEY_PLAIN, 1 , new Scalar (255, 122, 0), 2);
				}
			}

			Mat hierarchy = new Mat(imgTexture.height, imgTexture.width, CvType.CV_8UC3);
			List<MatOfPoint> blotContours = new List<MatOfPoint>();
			Imgproc.findContours (gray3Mat, blotContours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

			Texture2D texture = new Texture2D (imgMat.cols (), imgMat.rows (), TextureFormat.RGBA32, false);
			Utils.matToTexture2D (procMat, texture);
           
			GetComponent<RawImage> ().texture = (Texture2D)texture;
			        }

		public void slider1updated(){
			threshLev = Slider01.value;
		}
    }
}
