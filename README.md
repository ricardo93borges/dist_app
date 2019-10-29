Types

    Writer - a program that write in a file
    Reader - a program that read a file
    Archive - a program that keep a file, receive requests from Readers and Writer
    Coordinator - a program that manage the access to critical session (read the file), can be a former Writer or Reader, changes every 10 seconds
