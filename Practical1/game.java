//Abdelrahman Ahmed 24898008 && Hamdaan Mirza 24631494

import java.util.Random;

public class game {
    public static void main(String[] args) {
        Random random = new Random();
        int num1 = (int)(Math.random() * 1000) + 1;
        int num2 = (int)(Math.random() * 1000) + 1;
        int larger = 0, smaller = 0;

        if(num1 > num2) {
            larger = num1;
            smaller = num2;
        }
        else if(num2 > num1) {
            larger = num2;
            smaller = num1;
        }
        else {
            larger = num1;
            smaller = num1;
        }

        System.out.println("Content-type: text/html\n\n");
        System.out.println("<!DOCTYPE HTML>");
        System.out.println("<html lang=\"en\">");
        System.out.println("<head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"><title>Number Game</title>");
        System.out.println("<style>\r\n" + //
                        "        * {\r\n" + //
                        "            margin: 0;\r\n" + //
                        "            padding: 0;\r\n" + //
                        "            box-sizing: border-box;\r\n" + //
                        "            font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;\r\n" + //
                        "        }\r\n" + //
                        "\r\n" + //
                        "        body {\r\n" + //
                        "            background-color: #121212;\r\n" + //
                        "            height: 100vh;\r\n" + //
                        "            justify-content: center;\r\n" + //
                        "            align-items: center;\r\n" + //
                        "            text-align: center;\r\n" + //
                        "            display: flex;\r\n" + //
                        "        }\r\n" + //
                        "\r\n" + //
                        "        h1 {\r\n" + //
                        "            font-size: 2.2rem;\r\n" + //
                        "            margin-bottom: 0.5rem;\r\n" + //
                        "            background: linear-gradient(to right, #4facfe, #00f2fe);\r\n" + //
                        "            -webkit-background-clip: text;\r\n" + //
                        "            -webkit-text-fill-color: transparent;\r\n" + //
                        "        }\r\n" + //
                        "\r\n" + //
                        "        p {\r\n" + //
                        "            color: #bdc3c7;\r\n" + //
                        "            margin-bottom: 3rem;\r\n" + //
                        "            font-size: 1.1rem;\r\n" + //
                        "        }\r\n" + //
                        "\r\n" + //
                        "        .choices {\r\n" + //
                        "            display: flex;\r\n" + //
                        "            gap: 20px;\r\n" + //
                        "            justify-content: center;\r\n" + //
                        "            align-items: center;\r\n" + //
                        "            flex-wrap: wrap;\r\n" + //
                        "        }\r\n" + //
                        "\r\n" + //
                        "        .button {\r\n" + //
                        "            display: flex;\r\n" + //
                        "            justify-content: center;\r\n" + //
                        "            align-items: center;\r\n" + //
                        "            width: 160px;\r\n" + //
                        "            height: 160px;\r\n" + //
                        "            border-radius: 15px;\r\n" + //
                        "            border: 2px solid #4facfe;\r\n" + //
                        "            transition: all 0.3s ease;\r\n" + //
                        "            box-shadow: 0 4px 15px rgba(0, 0, 0, 0.3);\r\n" + //
                        "            background: #252525;\r\n" + //
                        "            color: #ffffff;\r\n" + //
                        "            font-size: 2.5rem;\r\n" + //
                        "            font-weight: bold;\r\n" + //
                        "            text-decoration: none;\r\n" + //
                        "        }\r\n" + //
                        "\r\n" + //
                        "        .button:hover {\r\n" + //
                        "            transform: scale(1.1);\r\n" + //
                        "            background: #4facfe;\r\n" + //
                        "            color: #121212;\r\n" + //
                        "            box-shadow: 0 0 25px rgba(79, 172, 254, 0.5);\r\n" + //
                        "        }\r\n" + //
                        "\r\n" + //
                        "        .button:active {\r\n" + 
                        "            transform: scale(1.0);\r\n" +
                        "        }\r\n" + 
                        "    </style></head>");
        System.out.println("<body><div>");
        System.out.println("<h1>Welcome to our Number Game!</h1>");
        System.out.println("<p>Select the larger number to win the game</p>");
        if(random.nextBoolean()){
            System.out.println("<div class = \"choices\"> <a href=\"/right.htm\" class=\"button\">" + larger + "</a>");
            System.out.println("<a href=\"/wrong.htm\" class=\"button\">" + smaller + "</a></div>");
        }
        else{
            System.out.println("<div class = \"choices\"><a href=\"/wrong.htm\" class=\"button\">" + smaller + "</a>");
            System.out.println("<a href=\"/right.htm\" class=\"button\">" + larger + "</a></div>");

        }
        System.out.println("</div></body>");
        System.out.println("</html>");
    }
}