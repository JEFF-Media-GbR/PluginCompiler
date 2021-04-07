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

    public static void main(String[] args) {
        Main compiler = new Main();

        if (args.length == 0) {
            System.out.println("Error: no plugin directory specified.");
            System.exit(1);
        }

        String pluginDirectory = args[0];

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

        /*banner("Running mvn clean on original source...");
        mavenRequest = new DefaultInvocationRequest();
        mavenRequest.setPomFile(new File(dir, "pom.xml"));
        mavenRequest.setGoals(Collections.singletonList("clean"));
        maven.execute(mavenRequest);
        System.out.println("Done.");*/

        banner("Cleaning up old build directories...");
        freeDir = new File(dir.getAbsolutePath() + " Free");
        plusDir = new File(dir.getAbsolutePath() + " Plus");
        FileUtils.deleteDirectory(freeDir);
        FileUtils.deleteDirectory(plusDir);
        System.out.println("Done.");

        banner("Copying source code...");
        freeDir.mkdir();
        plusDir.mkdir();
        FileUtils.copyDirectory(new File(dir,"src"), new File(freeDir,"src"));
        FileUtils.copyDirectory(new File(dir,"src"), new File(plusDir,"src"));
        FileUtils.copyFile(new File(dir,"pom.xml"),new File(freeDir,"pom.xml"));
        FileUtils.copyFile(new File(dir,"pom.xml"),new File(plusDir,"pom.xml"));
        System.out.println("Done.");

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

                if (line.contains("import de.jeff_media.daddy.Daddy;")) {
                    continue;
                }

                // Remove premium features
                if (line.contains("Daddy.allows")) {
                    daddyAllowsRemoved++;
                    line = line.replaceAll("Daddy\\.allows\\((.*?)\\)", "false");
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
                System.out.println(file.getName() + ": Replaced " + daddyAllowsRemoved + " Daddy.allows(...) checks with false");
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
        System.out.println("Done.");

        banner("Compiling Free version...");
        mavenRequest = new DefaultInvocationRequest();
        mavenRequest.setPomFile(new File(freeDir, "pom.xml"));
        mavenRequest.setGoals(goals);
        maven.execute(mavenRequest);
        System.out.println("Done.");
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
