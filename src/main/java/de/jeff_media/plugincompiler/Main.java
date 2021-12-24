package de.jeff_media.plugincompiler;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.Invoker;

import java.io.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Main {

    private File dir, freeDir, plusDir;
    private static String version = "";

    public static void main(String[] args) {
        Main compiler = new Main();

        if (args.length == 0) {
            System.out.println("Error: no plugin directory specified.");
            System.exit(1);
        }

        String pluginDirectory = args[0];
        if(args.length>1) version = args[1];

        try {
            compiler.run(pluginDirectory);
        } catch (Exception exception) {
            exception.printStackTrace();
        }

    }

    private static void banner(String text) {

        System.out.println(StringUtils.center("[ " + text + " ]", 80, "="));
    }

    public static String findExecutableOnPath(String name) {
        for (String dirname : System.getenv("PATH").split(File.pathSeparator)) {
            File file = new File(dirname, name);
            if (file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        throw new AssertionError("could not find executable " + name);
    }

    private void run(String pluginDirectory) throws Exception {
        InvocationRequest mavenRequest;
        Invoker maven = new DefaultInvoker();
        List<String> goals = Arrays.asList(new String[] {"clean","compile","package"});
        maven.setMavenHome(new File(findExecutableOnPath("mvn")).getParentFile().getParentFile());

        dir = new File(new File(System.getProperty("user.dir")), pluginDirectory);
        if (!dir.isDirectory()) {
            System.out.println("Directory " + dir.getAbsolutePath() + " not found.");
            System.exit(1);
        }

        final String mavenExecutable = findExecutableOnPath("mvn");

        banner("Cleaning up old build directories...");
        freeDir = new File(dir.getAbsolutePath() + " Free");
        plusDir = new File(dir.getAbsolutePath() + " Plus");
        FileUtils.deleteDirectory(freeDir);
        FileUtils.deleteDirectory(plusDir);
        System.out.println("Done.");

        banner("Copying source code...");
        freeDir.mkdir();
        plusDir.mkdir();
        System.out.println("Waiting 1000ms...");
        Thread.sleep(1000);

        System.out.println("Copying plus files...");
        Thread.sleep(1000);
        FileUtils.copyDirectory(new File(dir,"src"), new File(plusDir,"src"));
        FileUtils.copyDirectory(new File(dir,"allatori"), new File(plusDir,"allatori"));
        FileUtils.copyFile(new File(dir,"pom.xml"),new File(plusDir,"pom.xml"));
        Thread.sleep(1000);
        System.out.println("Done.");

        System.out.println("Copying free files...");
        Thread.sleep(1000);
        FileUtils.copyDirectory(new File(dir,"src"), new File(freeDir,"src"));
        FileUtils.copyFile(new File(dir,"pom.xml"),new File(freeDir,"pom.xml"));
        Thread.sleep(1000);

        banner("Removing files from free version...");
        new File(new File(new File(new File(freeDir,"main"),"java"),"resources"),"discord-verification.html").delete();

        banner("Patching source code for free version...");
        for (File file : FileUtils.listFiles(freeDir, null, true)) {

            FileType fileType = FileType.get(file);

            int daddyAllowsRemoved = 0;
            int linesRemovedTotal = 0;

            boolean commonCode = true;

            BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
            StringBuilder inputBuffer = new StringBuilder();
            String line;

            while ((line = bufferedReader.readLine()) != null) {

                // Ignore everything between /*Daddy start|end*/ comments
                if (line.contains("/*Daddy end*/")) {
                    commonCode = true;
                }

                if (line.contains("import de.jeff_media.daddy.Stepsister;")) {
                    continue;
                }

                // Remove premium features
                if (line.contains("Stepsister.allows")) {
                    daddyAllowsRemoved++;
                    line = line.replaceAll("Stepsister\\.allows\\((.*?)\\)", "false");
                }

                String startComment = "";
                String endComment = "";

                if (!commonCode) {
                    linesRemovedTotal++;
                    startComment = fileType.getStartComment();
                    endComment = fileType.getEndComment();
                }

                inputBuffer.append(startComment)
                        .append(line)
                        .append(endComment)
                        .append(System.lineSeparator());

                // Ignore everything between /*Daddy start|end*/ comments
                if (line.contains("/*Daddy start*/")) {
                    commonCode = false;
                }
            }
            bufferedReader.close();

            // write the new string with the replaced line OVER the same file
            FileOutputStream fileOut = new FileOutputStream(file);
            fileOut.write(inputBuffer.toString().getBytes());
            fileOut.close();
            if (daddyAllowsRemoved > 0) {
                System.out.println(file.getName() + ": Replaced " + daddyAllowsRemoved + " Stepsister.allows(...) checks with false");
            }
            if (linesRemovedTotal > 0) {
                System.out.println(file.getName() + ": Commented out " + linesRemovedTotal + " lines");
            }
        }
        System.out.println("Done.");

        banner("Compiling Plus version...");
        mavenRequest = new DefaultInvocationRequest();
        mavenRequest.setPomFile(new File(plusDir, "pom.xml"));
        mavenRequest.setGoals(goals);
        maven.execute(mavenRequest);
        System.out.println("Running allatori on Plus version...");
        File allatoriXml = new File(new File(plusDir,"target"),"allatori.xml");
        new File(new File(plusDir,"allatori"),"allatori-plus.xml").renameTo(allatoriXml);
        replaceVersionInAllatoriXml(allatoriXml);
        ProcessBuilder pb = new ProcessBuilder("java","-Xms128M", "-Xmx512M","-jar", "allatori/allatori.jar", "target/allatori.xml");
        pb.directory(plusDir);
        pb.inheritIO();
        Process p = pb.start();
        p.waitFor();
        System.out.println("Done.");

        banner("Compiling Free version...");
        FileUtils.copyDirectory(new File(dir,"allatori"), new File(freeDir,"allatori"));
        mavenRequest = new DefaultInvocationRequest();
        mavenRequest.setPomFile(new File(freeDir, "pom.xml"));
        mavenRequest.setGoals(goals);
        maven.execute(mavenRequest);
        System.out.println("Running allatori on Free version...");
        allatoriXml = new File(new File(freeDir,"target"),"allatori.xml");
        new File(new File(freeDir,"allatori"),"allatori-free.xml").renameTo(allatoriXml);
        replaceVersionInAllatoriXml(allatoriXml);
        pb = new ProcessBuilder("java","-Xms128M", "-Xmx512M","-jar", "allatori/allatori.jar", "target/allatori.xml");
        pb.directory(freeDir);
        pb.inheritIO();
        p = pb.start();
        p.waitFor();
        System.out.println("Done.");
    }

    private void replaceVersionInAllatoriXml(File allatoriXml) throws IOException {
        String content = FileUtils.readFileToString(allatoriXml, "UTF-8");
        content = content.replace("${project.version}",version);
        FileUtils.writeStringToFile(allatoriXml, content, "UTF-8");
    }

    private enum FileType {

        XML("<!--","-->"),
        JAVA("//",""),
        UNKNOWN("","");

        public String getStartComment() {
            return startComment;
        }

        public String getEndComment() {
            return endComment;
        }

        private final String startComment, endComment;

        FileType(String startComment, String endComment) {
            this.startComment = startComment;
            this.endComment = endComment;
        }

        public static FileType get(File file) {
            if(file.getName().endsWith(".java")) {
                return JAVA;
            }
            if(file.getName().endsWith(".xml")) {
                return XML;
            }
            return UNKNOWN;
        }
    }
}
