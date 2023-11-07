package uk.ac.manchester.tornado.examples.rodinia.particlefilter;

import java.util.Random;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.collections.types.*;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import static java.lang.Math.*;

public class ParticleFilter {
    static final double PI = 3.1415926535897932;
    // M value for Linear Congruential Generator (LCG); use GCC's value
    static long M = Integer.MAX_VALUE;
    // A value for LCG
    static int A = 1103515245;
    // C value for LCG
    static int C = 12345;

    //returns a long int representing the time
    public static long get_time() {
        return System.nanoTime();
    }

    // Returns the number of seconds elapsed between the two specified times
    public static float elapsed_time(long start_time, long end_time) {
        return (float) (end_time - start_time) / (1000000000);
    }

    // Takes in a double and returns an integer that approximates to that double
    // @return if the mantissa < .5 => return value < input value; else return value > input value
    public static double roundDouble(double value){
        int newValue = (int)(value);
        if(value - newValue < .5)
            return newValue;
        else
            return newValue++;
    }

    /*
     * Set values of the 3D array to a newValue if that value is equal to the testValue
     * @param testValue The value to be replaced
     * @param newValue The value to replace testValue with
     * @param array3D The image vector
     * @param dimX The x dimension of the frame
     * @param dimY The y dimension of the frame
     * @param dimZ The number of frames
     */
    public static void setIf(int testValue, int newValue, VectorInt array3D, int dimX, int dimY, int dimZ){
        int x, y, z;
        for(x = 0; x < dimX; x++){
            for(y = 0; y < dimY; y++){
                for(z = 0; z < dimZ; z++){
                    if(array3D.get(x * dimY * dimZ+y * dimZ + z) == testValue)
                    array3D.set(x * dimY * dimZ + y * dimZ + z, newValue);
                }
            }
        }
    }

    /*
     * Generates a uniformly distributed random number using the provided seed and GCC's settings for the Linear Congruential Generator (LCG)
     * @param seed The seed array
     * @param index The specific index of the seed to be advanced
     * @return a uniformly distributed number [0, 1)
     */
    public static double randu(VectorInt seed, int index)
    {
        int num = A*seed.get(index) + C;
        seed.set(index, (int) (num % M));
        return Math.abs(seed.get(index)/((double) M));
    }

    /*
     * Generates a normally distributed random number using the Box-Muller transformation
     * @param seed The seed array
     * @param index The specific index of the seed to be advanced
     * @return a double representing random number generated using the Box-Muller algorithm
     */
    public static double randn(VectorInt seed, int index){
        /*Box-Muller algorithm*/
        double u = randu(seed, index);
        double v = randu(seed, index);
        double cosine = cos(2*PI*v);
        double rt = -2*log(u);
        return sqrt(rt)*cosine;
    }

    /*
     * Sets values of 3D matrix using randomly generated numbers from a normal distribution
     * @param array3D The video to be modified
     * @param dimX The x dimension of the frame
     * @param dimY The y dimension of the frame
     * @param dimZ The number of frames
     * @param seed The seed array
     */
    public static void addNoise(VectorInt array3D, int dimX, int dimY, int dimZ, VectorInt seed){
        int x, y, z;
        for(x = 0; x < dimX; x++){
            for(y = 0; y < dimY; y++){
                for(z = 0; z < dimZ; z++){
                    array3D.set(x * dimY * dimZ + y * dimZ + z, array3D.get(x * dimY * dimZ + y * dimZ + z) + (int)(5*randn(seed, 0)));
                }
            }
        }
    }

    /*
     * Fills a radius x radius matrix representing the disk
     * @param disk The pointer to the disk to be made
     * @param radius  The radius of the disk to be made
     */
    public static void strelDisk(VectorInt disk, int radius) {
        int diameter = radius*2 - 1;
        int x, y;
        for(x = 0; x < diameter; x++){
            for(y = 0; y < diameter; y++){
                double distance = sqrt(pow((double)(x-radius+1),2) + pow((double)(y-radius+1),2));
                if(distance < radius)
                    disk.set(x*diameter + y, 1);
            }
        }
    }

    /*
     * Dilates the provided video
     * @param matrix The video to be dilated
     * @param posX The x location of the pixel to be dilated
     * @param posY The y location of the pixel to be dilated
     * @param poxZ The z location of the pixel to be dilated
     * @param dimX The x dimension of the frame
     * @param dimY The y dimension of the frame
     * @param dimZ The number of frames
     * @param error The error radius
     */
    public static void dilate_matrix(VectorInt matrix, int posX, int posY, int posZ, int dimX, int dimY, int dimZ, int error) {
        int startX = posX - error;
        while(startX < 0)
            startX++;
        int startY = posY - error;
        while(startY < 0)
            startY++;
        int endX = posX + error;
        while(endX > dimX)
            endX--;
        int endY = posY + error;
        while(endY > dimY)
            endY--;
        int x,y;
        for(x = startX; x < endX; x++){
            for(y = startY; y < endY; y++){
                double distance = sqrt( pow((double)(x-posX),2) + pow((double)(y-posY),2) );
                if(distance < error)
                    matrix.set(x*dimY*dimZ + y*dimZ + posZ, 1);
            }
        }
    }

    /*
     * Dilates the target matrix using the radius as a guide
     * @param matrix The reference matrix
     * @param dimX The x dimension of the video
     * @param dimY The y dimension of the video
     * @param dimZ The z dimension of the video
     * @param error The error radius to be dilated
     * @param newMatrix The target matrix
     */
    public static void imdilate_disk(VectorInt matrix, int dimX, int dimY, int dimZ, int error, VectorInt newMatrix) {
        int x, y, z;
        for(z = 0; z < dimZ; z++){
            for(x = 0; x < dimX; x++){
                for(y = 0; y < dimY; y++){
                    if(matrix.get(x*dimY*dimZ + y*dimZ + z) == 1){
                        dilate_matrix(newMatrix, x, y, z, dimX, dimY, dimZ, error);
                    }
                }
            }
        }
    }

    /*
     * Fills a 2D array describing the offsets of the disk object
     * @param se The disk object
     * @param numOnes The number of ones in the disk
     * @param neighbors The array that will contain the offsets
     * @param radius The radius used for dilation
     */
    public static void getneighbors(VectorInt se, int numOnes, VectorDouble neighbors, int radius){
        int x, y;
        int neighY = 0;
        int center = radius - 1;
        int diameter = radius*2 -1;
        for(x = 0; x < diameter; x++){
            for(y = 0; y < diameter; y++){
                if(se.get(x*diameter + y) != 0){
                    neighbors.set(neighY*2, (int)(y - center));
                    neighbors.set(neighY*2 + 1, (int)(x - center));
                    neighY++;
                }
            }
        }
    }

    /*
     * The synthetic video sequence we will work with here is composed of a
     * single moving object, circular in shape (fixed radius)
     * The motion here is a linear motion
     * the foreground intensity and the backgrounf intensity is known
     * the image is corrupted with zero mean Gaussian noise
     * @param I The video itself
     * @param IszX The x dimension of the video
     * @param IszY The y dimension of the video
     * @param Nfr The number of frames of the video
     * @param seed The seed array used for number generation
     */
   public static void videoSequence(VectorInt I, int IszX, int IszY, int Nfr, VectorInt seed){
        int k;
        int max_size = IszX*IszY*Nfr;
        /*get object centers*/
        int x0 = (int)roundDouble(IszY/2.0);
        int y0 = (int)roundDouble(IszX/2.0);
        I.set(x0 *IszY *Nfr + y0 * Nfr  + 0, 1);

        /*move point*/
        int xk, yk, pos;
        for(k = 1; k < Nfr; k++){
            xk = abs(x0 + (k-1));
            yk = abs(y0 - 2*(k-1));
            pos = yk * IszY * Nfr + xk *Nfr + k;
            if(pos >= max_size)
                pos = 0;
            I.set(pos, 1);
        }

        /*dilate matrix*/
        VectorInt newMatrix = new VectorInt(IszX*IszY*Nfr);
        imdilate_disk(I, IszX, IszY, Nfr, 5, newMatrix);
        int x, y;
        for(x = 0; x < IszX; x++){
            for(y = 0; y < IszY; y++){
                for(k = 0; k < Nfr; k++){
                    I.set(x*IszY*Nfr + y*Nfr + k, newMatrix.get(x*IszY*Nfr + y*Nfr + k));
                }
            }
        }

        /*define background, add noise*/
        setIf(0, 100, I, IszX, IszY, Nfr);
        setIf(1, 228, I, IszX, IszY, Nfr);
        /*add noise*/
        addNoise(I, IszX, IszY, Nfr, seed);
   }

    /*
     * Determines the likelihood sum based on the formula: SUM( (IK[IND] - 100)^2 - (IK[IND] - 228)^2)/ 100
     * @param I The 3D matrix
     * @param ind The current ind array
     * @param numOnes The length of ind array
     * @return A double representing the sum
     */
    public static double calcLikelihoodSum(VectorInt I, VectorInt ind, int numOnes){
        double likelihoodSum = 0.0;
        int y;
        for(y = 0; y < numOnes; y++)
            likelihoodSum += (pow((I.get(ind.get(y)) - 100),2) - pow((I.get(ind.get(y))-228),2))/50.0;
        return likelihoodSum;
    }

    /*
     * Finds the first element in the CDF that is greater than or equal to the provided value and returns that index
     * @note This function uses sequential search
     * @param CDF The CDF
     * @param lengthCDF The length of CDF
     * @param value The value to be found
     * @return The index of value in the CDF; if value is never found, returns the last index
     */
    public static int findIndex(VectorDouble CDF, int lengthCDF, double value){
        int index = -1;
        int x;
        for(x = 0; x < lengthCDF; x++){
            if(CDF.get(x) >= value){
                index = x;
                break;
            }
        }
        if(index == -1){
            return lengthCDF-1;
        }
        return index;
    }

    /*
     * Finds the first element in the CDF that is greater than or equal to the provided value and returns that index
     * @note This function uses binary search before switching to sequential search
     * @param CDF The CDF
     * @param beginIndex The index to start searching from
     * @param endIndex The index to stop searching
     * @param value The value to find
     * @return The index of value in the CDF; if value is never found, returns the last index
     * @warning Use at your own risk; not fully tested
     */
    public static int findIndexBin(VectorDouble CDF, int beginIndex, int endIndex, double value){
        if(endIndex < beginIndex)
            return -1;
        int middleIndex = beginIndex + ((endIndex - beginIndex)/2);
        /*check the value*/
        if(CDF.get(middleIndex) >= value)
        {
            /*check that it's good*/
            if(middleIndex == 0)
                return middleIndex;
            else if(CDF.get(middleIndex-1) < value)
                return middleIndex;
            else if(CDF.get(middleIndex-1) == value)
            {
                while(middleIndex > 0 && CDF.get(middleIndex-1) == value)
                    middleIndex--;
                return middleIndex;
            }
        }
        if(CDF.get(middleIndex) > value)
            return findIndexBin(CDF, beginIndex, middleIndex+1, value);
        return findIndexBin(CDF, middleIndex-1, endIndex, value);
    }

    /*
     * The implementation of the particle filter using OpenMP for many frames
     * This function is designed to work with a video of several frames. In addition, it references a provided MATLAB function which takes the video, the objxy matrix and the x and y arrays as arguments and returns the likelihoods
     * @param I The video to be run
     * @param IszX The x dimension of the video
     * @param IszY The y dimension of the video
     * @param Nfr The number of frames
     * @param seed The seed array used for random number generation
     * @param Nparticles The number of particles to be used
     */

    public static void setWeights(VectorDouble weights){
        for(@Parallel int x = 0; x < weights.size(); x++){
            weights.set(x, 1/((double)(weights.size())));
        }
    }

    public static void setArrayXY(VectorDouble arrayX, VectorDouble arrayY, Double2 xeye){
        for(@Parallel int x = 0; x < arrayX.size(); x++){
            arrayX.set(x, xeye.getX());
            arrayY.set(x, xeye.getY());
        }
    }

    public static void updateArrayXY(VectorDouble arrayX, VectorDouble arrayY, VectorInt seed){
        for(@Parallel int x = 0; x < arrayX.size(); x++){
            arrayX.set(x, arrayX.get(x) + 1 + 5*randn(seed, x));
            arrayY.set(x, arrayY.get(x) + -2 + 2*randn(seed, x));
        }
    }

    public static void computeLikelihood(VectorDouble arrayX, VectorDouble arrayY, VectorDouble objxy, Int2 indxy, VectorInt ind, VectorDouble likelihood, Int4 paras, VectorInt I){
        for( int x = 0; x < arrayX.size(); x++){
            for( int y = 0; y < (objxy.size()/2); y++){
                indxy.setX((int) (roundDouble(arrayX.get(x)) + objxy.get(y*2 + 1)));
                indxy.setY((int) (roundDouble(arrayY.get(x)) + objxy.get(y*2)));
                ind.set(x*(objxy.size()/2) + y, Math.abs(indxy.getX()* paras.getY()* paras.getZ() + paras.getY()* paras.getZ() + paras.getW()));
                if(ind.get(x*(objxy.size()/2) + y) >= paras.getX()* paras.getY()* paras.getZ())
                    ind.set(x*(objxy.size()/2) + y, 0);
            }
            likelihood.set(x, 0);
            for( int y = 0; y < objxy.size()/2; y++)
                likelihood.set(x, likelihood.get(x) + (pow((I.get(ind.get(x*objxy.size()/2 + y)) - 100),2) - pow((I.get(ind.get(x*(objxy.size()/2) + y))-228),2))/50.0);
            likelihood.set(x, likelihood.get(x)/((double) objxy.size()/2));
        }
    }

    public static void updateWeights(VectorDouble weights, VectorDouble likelihood){
        for(@Parallel int x = 0; x < weights.size(); x++){
            weights.set(x, weights.get(x) * exp(likelihood.get(x)));
        }
    }


    public static void computeSumWeights(VectorDouble weights, VectorDouble sumWeights){
        for(@Parallel int x = 0; x < weights.size(); x++){
            sumWeights.set(0, sumWeights.get(0) + weights.get(x)); //sumWeights = sumWeights + weights.get(x);
        }
    }

    public static void normaliseWeights(VectorDouble weights, VectorDouble sumWeights){
        for(@Parallel int x = 0; x < weights.size(); x++){
            weights.set(x, weights.get(x)/sumWeights.get(0));
        }
    }

    public static void moveObjects(VectorDouble arrayX, VectorDouble arrayY, VectorDouble weights, Double2 xeye){
        for(@Parallel int x = 0; x < weights.size(); x++){
            xeye.setX(xeye.getX()+arrayX.get(x) * weights.get(x));// xe += arrayX.get(x) * weights.get(x);
            xeye.setY(xeye.getY()+arrayY.get(x) * weights.get(x));// ye += arrayY.get(x) * weights.get(x);
        }
    }

    public static void findU(VectorDouble u, VectorDouble u1){
        for(@Parallel int x = 0; x < u.size(); x++){
            u.set(x, u1.get(0) + x/((double)(u.size())));
        }
    }

    public static void findNewArray(VectorDouble u, VectorDouble arrayX, VectorDouble arrayY, VectorDouble CDF, VectorDouble xj, VectorDouble yj){
        for(@Parallel int j = 0; j < u.size(); j++){
            int i = findIndex(CDF, u.size(), u.get(j));
            if(i == -1){
                i = u.size() - 1;
            }
            xj.set(j, arrayX.get(i));
            yj.set(j, arrayY.get(i));
        }
    }

    public static void resetWeights(VectorDouble arrayX, VectorDouble arrayY, VectorDouble xj, VectorDouble yj, VectorDouble weights) {
        for(@Parallel int x = 0; x < weights.size(); x++){
            //reassign arrayX and arrayY
            arrayX.set(x, xj.get(x));
            arrayY.set(x, yj.get(x));
            weights.set(x, 1/((double)(weights.size())));
        }
    }

    public static void particleFilter(VectorInt I, int IszX, int IszY, int Nfr, VectorInt seed, int Nparticles){
        int max_size = IszX*IszY*Nfr;
        long start = get_time();

        Double2 xeye = new Double2();
        xeye.setX(roundDouble(IszY/2.0));
        xeye.setY(roundDouble(IszX/2.0));
        Int2 indxy = new Int2();
        indxy.setX(0);
        indxy.setY(0);
        Int4 paras = new Int4();
        paras.setX(IszX);
        paras.setY(IszY);
        paras.setZ(Nfr);
        //original particle centroid
//        double xe = roundDouble(IszY/2.0);
//        double ye = roundDouble(IszX/2.0);

        //expected object locations, compared to center
        int radius = 5;
        int diameter = radius*2 - 1;
        VectorInt disk = new VectorInt(diameter*diameter);
        strelDisk(disk, radius);
        int countOnes = 0;
        int x, y;
        for(x = 0; x < diameter; x++){
            for(y = 0; y < diameter; y++){
                if(disk.get(x*diameter + y) == 1)
                    countOnes++;
            }
        }
        VectorDouble objxy = new VectorDouble(countOnes*2);
        getneighbors(disk, countOnes, objxy, radius);

        long get_neighbors = get_time();
        System.out.printf("TIME TO GET NEIGHBORS TOOK: %f\n", elapsed_time(start, get_neighbors));
        //initial weights are all equal (1/Nparticles)
        VectorDouble weights = new VectorDouble(Nparticles);
//        for(x = 0; x < Nparticles; x++){
//            weights.set(x, 1/((double)(Nparticles)));
//        }
        TornadoDevice device = TornadoRuntime.getTornadoRuntime().getDefaultDevice();
        TaskGraph taskGraph1 = new TaskGraph("s1")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, weights) //
                .task("t1", ParticleFilter::setWeights, weights) // no_of_nodes,
                .transferToHost(DataTransferMode.EVERY_EXECUTION, weights);
        ImmutableTaskGraph immutableTaskGraph1 = taskGraph1.snapshot();
        TornadoExecutionPlan executor1 = new TornadoExecutionPlan(immutableTaskGraph1)
                .withDevice(device);
        executor1.execute();
        long get_weights = get_time();
        System.out.printf("TIME TO GET WEIGHTSTOOK: %f\n", elapsed_time(get_neighbors, get_weights));
        //initial likelihood to 0.0
        VectorDouble likelihood = new VectorDouble(Nparticles);
        VectorDouble arrayX = new VectorDouble(Nparticles);
        VectorDouble arrayY = new VectorDouble(Nparticles);
        VectorDouble xj = new VectorDouble(Nparticles);
        VectorDouble yj = new VectorDouble(Nparticles);
        VectorDouble CDF = new VectorDouble(Nparticles);
        VectorDouble u = new VectorDouble(Nparticles);
        VectorInt ind = new VectorInt(countOnes*Nparticles);
//        for(x = 0; x < Nparticles; x++){
//            arrayX.set(x, xeye.getX());
//            arrayY.set(x, xeye.getY());
//        }
        TaskGraph taskGraph2 = new TaskGraph("s2")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, xeye) //
                .task("t2", ParticleFilter::setArrayXY, arrayX, arrayY, xeye) // no_of_nodes,
                .transferToHost(DataTransferMode.EVERY_EXECUTION, arrayX, arrayY);
        ImmutableTaskGraph immutableTaskGraph2 = taskGraph2.snapshot();
        TornadoExecutionPlan executor2 = new TornadoExecutionPlan(immutableTaskGraph2)
                .withDevice(device);
        executor2.execute();
//        taskGraph1 = new TaskGraph("s1")
//                .transferToDevice(DataTransferMode.EVERY_EXECUTION, xeye)
//                .task("t1", ParticleFilter::setArrayXY, arrayX, arrayY, xeye)
//                .transferToHost(DataTransferMode.EVERY_EXECUTION, arrayX, arrayY);
//        immutableTaskGraph1 = taskGraph1.snapshot();
//        executor1 = new TornadoExecutionPlan(immutableTaskGraph1)
//                .withDevice(device);
//        executor1.execute();


        System.out.printf("TIME TO SET ARRAYS TOOK: %f\n", elapsed_time(get_weights, get_time()));
        int indX, indY;
        for(int k = 1; k < Nfr; k++){
            paras.setW(k);
            long set_arrays = get_time();
            //apply motion model
            //draws sample from motion model (random walk). The only prior information
            //is that the object moves 2x as fast as in the y direction
//            for(x = 0; x < Nparticles; x++){
//                arrayX.set(x, arrayX.get(x) + 1 + 5*randn(seed, x));
//                arrayY.set(x, arrayY.get(x) + -2 + 2*randn(seed, x));
//            }
            TaskGraph taskGraph3 = new TaskGraph("s3")
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, arrayX, arrayY, seed)
                    .task("t3", ParticleFilter::updateArrayXY, arrayX, arrayY, seed)
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, arrayX, arrayY);
            ImmutableTaskGraph immutableTaskGraph3 = taskGraph3.snapshot();
            TornadoExecutionPlan executor3 = new TornadoExecutionPlan(immutableTaskGraph3)
                    .withDevice(device);
            executor3.execute();
//            taskGraph1 = new TaskGraph("s1")
//                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, arrayX, arrayY, seed)
//                    .task("t1", ParticleFilter::updateArrayXY, arrayX, arrayY, seed)
//                    .transferToHost(DataTransferMode.EVERY_EXECUTION, arrayX, arrayY);
//            immutableTaskGraph1 = taskGraph1.snapshot();
//            executor1 = new TornadoExecutionPlan(immutableTaskGraph1)
//                    .withDevice(device);
//            executor1.execute();


            long error = get_time();
            System.out.printf("TIME TO SET ERROR TOOK: %f\n", elapsed_time(set_arrays, error));
            //particle filter likelihood
//            for(x = 0; x < Nparticles; x++){
//                for(y = 0; y < countOnes; y++){
//                    indX = (int) (roundDouble(arrayX.get(x)) + objxy.get(y*2 + 1));
//                    indY = (int) (roundDouble(arrayY.get(x)) + objxy.get(y*2));
//                    ind.set(x*countOnes + y, Math.abs(indX*IszY*Nfr + indY*Nfr + k));
//                    if(ind.get(x*countOnes + y) >= max_size)
//                        ind.set(x*countOnes + y, 0);
//                }
//                likelihood.set(x, 0);
//                for(y = 0; y < countOnes; y++)
//                    likelihood.set(x, likelihood.get(x) + (pow((I.get(ind.get(x*countOnes + y)) - 100),2) - pow((I.get(ind.get(x*countOnes + y))-228),2))/50.0);
//                likelihood.set(x, likelihood.get(x)/((double) countOnes));
//            }

            computeLikelihood(arrayX, arrayY, objxy, indxy, ind, likelihood, paras, I);
//            TaskGraph taskGraph11 = new TaskGraph("s11")
//                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, arrayX, arrayY, objxy, indxy, ind, likelihood, paras, I)
//                    .task("t11", ParticleFilter::computeLikelihood, arrayX, arrayY, objxy, indxy, ind, likelihood, paras, I)
//                    .transferToHost(DataTransferMode.EVERY_EXECUTION, arrayX, arrayY, objxy, indxy, ind, likelihood, paras, I);
//            ImmutableTaskGraph immutableTaskGraph11 = taskGraph11.snapshot();
//            TornadoExecutionPlan executor11 = new TornadoExecutionPlan(immutableTaskGraph11)
//                    .withDevice(device);
//            executor11.execute();


            long likelihood_time = get_time();
            System.out.printf("TIME TO GET LIKELIHOODS TOOK: %f\n", elapsed_time(error, likelihood_time));
            // update & normalize weights
            // using equation (63) of Arulampalam Tutorial
//            for(x = 0; x < Nparticles; x++){
//                weights.set(x, weights.get(x) * exp(likelihood.get(x)));
//            }
            TaskGraph taskGraph4 = new TaskGraph("s4")
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, weights, likelihood)
                    .task("t4", ParticleFilter::updateWeights, weights, likelihood)
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, weights);
            ImmutableTaskGraph immutableTaskGraph4 = taskGraph3.snapshot();
            TornadoExecutionPlan executor4 = new TornadoExecutionPlan(immutableTaskGraph4)
                    .withDevice(device);
            executor4.execute();
//            taskGraph1 = new TaskGraph("s1")
//                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, weights, likelihood)
//                    .task("t1", ParticleFilter::updateWeights, weights, likelihood)
//                    .transferToHost(DataTransferMode.EVERY_EXECUTION, weights);
//            immutableTaskGraph1 = taskGraph1.snapshot();
//            executor1 = new TornadoExecutionPlan(immutableTaskGraph1)
//                    .withDevice(device);
//            executor1.execute();


            long exponential = get_time();
            System.out.printf("TIME TO GET EXP TOOK: %f\n", elapsed_time(likelihood_time, exponential));
            VectorDouble sumWeights = new VectorDouble(1); // double sumWeights = 0;
//            for(x = 0; x < Nparticles; x++){
//                sumWeights += weights.get(x);
//            }
//            computeSumWeights(weights, sumWeights);
            TaskGraph taskGraph5 = new TaskGraph("s5")
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, weights, sumWeights)
                    .task("t5", ParticleFilter::computeSumWeights, weights, sumWeights)
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, sumWeights);
            ImmutableTaskGraph immutableTaskGraph5 = taskGraph5.snapshot();
            TornadoExecutionPlan executor5 = new TornadoExecutionPlan(immutableTaskGraph5)
                    .withDevice(device);
            executor5.execute();
//            taskGraph1 = new TaskGraph("s1")
//                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, weights, sumWeights) //
//                    .task("t1", ParticleFilter::computeSumWeights, weights, sumWeights) // no_of_nodes,
//                    .transferToHost(DataTransferMode.EVERY_EXECUTION, sumWeights);
//            immutableTaskGraph1 = taskGraph1.snapshot();
//            executor1 = new TornadoExecutionPlan(immutableTaskGraph1)
//                    .withDevice(device);
//            executor1.execute();


            long sum_time = get_time();
            System.out.printf("TIME TO SUM WEIGHTS TOOK: %f\n", elapsed_time(exponential, sum_time));
//            for(x = 0; x < Nparticles; x++){
//                weights.set(x, weights.get(x)/sumWeights.get(0));
//            }
//            normaliseWeights(weights, sumWeights);
            TaskGraph taskGraph6 = new TaskGraph("s6")
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, weights, sumWeights)
                    .task("t6", ParticleFilter::normaliseWeights, weights, sumWeights)
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, weights);
            ImmutableTaskGraph immutableTaskGraph6 = taskGraph6.snapshot();
            TornadoExecutionPlan executor6 = new TornadoExecutionPlan(immutableTaskGraph6)
                    .withDevice(device);
            executor6.execute();
//            taskGraph1 = new TaskGraph("s1")
//                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, weights, sumWeights) //
//                    .task("t1", ParticleFilter::normaliseWeights, weights, sumWeights) // no_of_nodes,
//                    .transferToHost(DataTransferMode.EVERY_EXECUTION, weights);
//            immutableTaskGraph1 = taskGraph1.snapshot();
//            executor1 = new TornadoExecutionPlan(immutableTaskGraph1)
//                    .withDevice(device);
//            executor1.execute();
            long normalize = get_time();
            System.out.printf("TIME TO NORMALIZE WEIGHTS TOOK: %f\n", elapsed_time(sum_time, normalize));
            xeye.setX(0);// xe = 0;
            xeye.setY(0);// ye = 0;
            // estimate the object location by expected values
//            for(x = 0; x < Nparticles; x++){
//                xeye.setX(xeye.getX()+arrayX.get(x) * weights.get(x));// xe += arrayX.get(x) * weights.get(x);
//                xeye.setY(xeye.getY()+arrayY.get(x) * weights.get(x));// ye += arrayY.get(x) * weights.get(x);
//            }
//            moveObjects(arrayX, arrayY, weights, xeye);
            TaskGraph taskGraph10 = new TaskGraph("s10")
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, arrayX, arrayY, weights, xeye)
                    .task("t10", ParticleFilter::moveObjects, arrayX, arrayY, weights, xeye)
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, xeye);
            ImmutableTaskGraph immutableTaskGraph10 = taskGraph10.snapshot();
            TornadoExecutionPlan executor10 = new TornadoExecutionPlan(immutableTaskGraph1)
                    .withDevice(device);
            executor10.execute();
//            taskGraph1 = new TaskGraph("s1")
//                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, arrayX, arrayY, weights, xeye)
//                    .task("t1", ParticleFilter::moveObjects, arrayX, arrayY, weights, xeye)
//                    .transferToHost(DataTransferMode.EVERY_EXECUTION, arrayX, arrayY, weights, xeye);
//            immutableTaskGraph1 = taskGraph1.snapshot();
//            executor1 = new TornadoExecutionPlan(immutableTaskGraph1)
//                    .withDevice(device);
//            executor1.execute();
            long move_time = get_time();
            System.out.printf("TIME TO MOVE OBJECT TOOK: %f\n", elapsed_time(normalize, move_time));
            System.out.printf("XE: %f\n", xeye.getX());// System.out.printf("XE: %f\n", xe);
            System.out.printf("YE: %f\n", xeye.getY());// System.out.printf("YE: %f\n", ye);
            double distance = sqrt( pow((double)(xeye.getX()-(int)roundDouble(IszY/2.0)),2) + pow((double)(xeye.getY()-(int)roundDouble(IszX/2.0)),2) );
            //double distance = sqrt( pow((double)(xe-(int)roundDouble(IszY/2.0)),2) + pow((double)(ye-(int)roundDouble(IszX/2.0)),2) );
            System.out.printf("%f\n", distance);

            CDF.set(0, weights.get(0));
            for(x = 1; x < Nparticles; x++){
                CDF.set(x, weights.get(x) + CDF.get(x-1));
            }
            long cum_sum = get_time();
            System.out.printf("TIME TO CALC CUM SUM TOOK: %f\n", elapsed_time(move_time, cum_sum));
            VectorDouble u1 = new VectorDouble(1);
            u1.set(0, (1/((double)(Nparticles)))*randu(seed, 0)); //double u1 = (1/((double)(Nparticles)))*randu(seed, 0);
//            for(x = 0; x < Nparticles; x++){
//                u.set(x, u1 + x/((double)(Nparticles)));
//            }
//            findU(u, u1);
            TaskGraph taskGraph7 = new TaskGraph("s7")
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, u, u1)
                    .task("t7", ParticleFilter::normaliseWeights, u, u1)
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, u);
            ImmutableTaskGraph immutableTaskGraph7 = taskGraph7.snapshot();
            TornadoExecutionPlan executor7 = new TornadoExecutionPlan(immutableTaskGraph7)
                    .withDevice(device);
            executor7.execute();
//            taskGraph1 = new TaskGraph("s1")
//                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, u, u1)
//                    .task("t1", ParticleFilter::findU, u, u1)
//                    .transferToHost(DataTransferMode.EVERY_EXECUTION, u);
//            immutableTaskGraph1 = taskGraph1.snapshot();
//            executor1 = new TornadoExecutionPlan(immutableTaskGraph1)
//                    .withDevice(device);
//            executor1.execute();


            long u_time = get_time();
            System.out.printf("TIME TO CALC U TOOK: %f\n", elapsed_time(cum_sum, u_time));
            int j, i;

//            for(j = 0; j < Nparticles; j++){
//                i = findIndex(CDF, Nparticles, u.get(j));
//                if(i == -1){
//                    i = Nparticles-1;
//                }
//                xj.set(j, arrayX.get(i));
//                yj.set(j, arrayY.get(i));
//            }
//            findNewArray(u, arrayX, arrayY, CDF, xj, yj);
            TaskGraph taskGraph8 = new TaskGraph("s8")
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, u, arrayX, arrayY, CDF, xj, yj)
                    .task("t8", ParticleFilter::findNewArray, u, arrayX, arrayY, CDF, xj, yj)
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, xj, yj);
            ImmutableTaskGraph immutableTaskGraph8 = taskGraph8.snapshot();
            TornadoExecutionPlan executor8 = new TornadoExecutionPlan(immutableTaskGraph8)
                    .withDevice(device);
            executor8.execute();
//            taskGraph1 = new TaskGraph("s1")
//                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, u, arrayX, arrayY, CDF, xj, yj)
//                    .task("t1", ParticleFilter::findNewArray, u, arrayX, arrayY, CDF, xj, yj)
//                    .transferToHost(DataTransferMode.EVERY_EXECUTION, xj, yj);
//            immutableTaskGraph1 = taskGraph1.snapshot();
//            executor1 = new TornadoExecutionPlan(immutableTaskGraph1)
//                    .withDevice(device);
//            executor1.execute();
            long xyj_time = get_time();
            System.out.printf("TIME TO CALC NEW ARRAY X AND Y TOOK: %f\n", elapsed_time(u_time, xyj_time));

            //#pragma omp parallel for shared(weights, Nparticles) private(x)
//            for(x = 0; x < Nparticles; x++){
//                //reassign arrayX and arrayY
//                arrayX.set(x, xj.get(x));
//                arrayY.set(x, yj.get(x));
//                weights.set(x, 1/((double)(Nparticles)));
//            }
            TaskGraph taskGraph9 = new TaskGraph("s9")
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, xj, yj, weights)
                    .task("t9", ParticleFilter::resetWeights, arrayX, arrayY, xj, yj, weights)
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, arrayX, arrayY, weights);
            ImmutableTaskGraph immutableTaskGraph9 = taskGraph9.snapshot();
            TornadoExecutionPlan executor9 = new TornadoExecutionPlan(immutableTaskGraph9)
                    .withDevice(device);
            executor9.execute();
//            taskGraph1 = new TaskGraph("s1")
//                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, xj, yj, weights)
//                    .task("t1", ParticleFilter::resetWeights, arrayX, arrayY, xj, yj, weights)
//                    .transferToHost(DataTransferMode.EVERY_EXECUTION, arrayX, arrayY, weights);
//            immutableTaskGraph1 = taskGraph1.snapshot();
//            executor1 = new TornadoExecutionPlan(immutableTaskGraph1)
//                    .withDevice(device);
//            executor1.execute();
            long reset = get_time();
            System.out.printf("TIME TO RESET WEIGHTS TOOK: %f\n", elapsed_time(xyj_time, reset));
        }
    }

    public static void main(String[] args){
        String usage = "ParticleFilter x <dimX> y <dimY> z <Nfr> np <Nparticles>";
        //check number of arguments
        if(args.length != 8) {
            System.out.printf("%s\n", usage);
            System.exit(1);
        }
        //check args deliminators
        if( (!args[0].equals("x")) ||  (!args[2].equals("y")) || (!args[4].equals("z")) || (!args[6].equals("np")) ) {
            System.out.printf( "%s\n",usage );
            System.exit(1);
        }

        int IszX, IszY, Nfr, Nparticles;

        //converting a string to a integer
//        if( sscanf( argv[2], "%d", &IszX ) == EOF ) {
//            printf("ERROR: dimX input is incorrect");
//            return 0;
//        }

        if( Integer.parseInt(args[1]) <= 0 ) {
            System.out.printf("dimX must be > 0\n");
            System.exit(1);
        }
        IszX = Integer.parseInt(args[1]);

        //converting a string to a integer
//        if( sscanf( argv[4], "%d", &IszY ) == EOF ) {
//            printf("ERROR: dimY input is incorrect");
//            return 0;
//        }

        if( Integer.parseInt(args[3]) <= 0 ) {
            System.out.printf("dimY must be > 0\n");
            System.exit(1);
        }
        IszY = Integer.parseInt(args[3]);


        //converting a string to a integer
//        if( sscanf( argv[6], "%d", &Nfr ) == EOF ) {
//            printf("ERROR: Number of frames input is incorrect");
//            return 0;
//        }

        if( Integer.parseInt(args[5]) <= 0 ) {
            System.out.printf("number of frames must be > 0\n");
            System.exit(1);
        }
        Nfr = Integer.parseInt(args[5]);

        //converting a string to a integer
//        if( sscanf( argv[8], "%d", &Nparticles ) == EOF ) {
//            printf("ERROR: Number of particles input is incorrect");
//            return 0;
//        }

        if( Integer.parseInt(args[7]) <= 0 ) {
            System.out.print("Number of particles must be > 0\n");
            System.exit(1);
        }
        Nparticles = Integer.parseInt(args[7]);

        //establish seed
        VectorInt seed = new VectorInt(Nparticles);
        int i;
        Random random = new Random();
        for(i = 0; i < Nparticles; i++) {
            seed.set(i, random.nextInt() * i);
        }
        //malloc matrix
        VectorInt I = new VectorInt(IszX*IszY*Nfr);
        long start = get_time();
        //call video sequence
        videoSequence(I, IszX, IszY, Nfr, seed);
        long endVideoSequence = get_time();
        System.out.printf("VIDEO SEQUENCE TOOK %f\n", elapsed_time(start, endVideoSequence));
        //call particle filter
        particleFilter(I, IszX, IszY, Nfr, seed, Nparticles);
        long endParticleFilter = get_time();
        System.out.printf("PARTICLE FILTER TOOK %f\n", elapsed_time(endVideoSequence, endParticleFilter));
        System.out.printf("ENTIRE PROGRAM TOOK %f\n", elapsed_time(start, endParticleFilter));
    }
}
