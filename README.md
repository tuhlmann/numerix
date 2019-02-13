# AGYNAMIX Numerix

For instructions to build and run the application please see the end of this file.

I started Numerix in 2015 (-ish) as a personal project to get deeper into Clojure. It worked :)

Unfortunately the application never was deployed into production, probably due to loss of focus and lack of time.
I publish the code here in the hope some parts of it may prove useful to someone else.

## Overview

Numerix was planned as a multi tenant tool for freelancers or small companies. It contains modules for:

- time tracking, 
- invoice generation (from tracked time and extra items)
- document management, multi document upload
- a knowledge base
- chat rooms, complete with callout and notifications
- user management, complete with notification emails, password reset, etc.
- Calendar

On the technical side, Numerix is a Clojure / Clojurescript project using Reagent and re-frame, storing data into a MongoDB.
It uses a [role based permissions system](https://github.com/tuhlmann/permissions) similar 
to [Apache Shiro's wildcard permissions](http://shiro.apache.org/permissions.html).

## Screenshot

Let me show you a few screens to make this dry readme a bit more colorful.

When starting the app the first time you'll be greeted by a landing page:

![Landing Page](/wiki/img/numerix-landing.png)

After logging in to the server - it uses cookie based extended sessions so you'll be logged in if you come back later on, you'll see a dashboard:

![Dashboard](/wiki/img/numerix-dashboard.png)

To the left you see a list of modules available to you. An admin can grant you (read/edit your own stuff / edit all) access to a module. The sidebar can also be minimized to save screen real estate.

You can keep a timeroll:
![Timeroll Overview](/wiki/img/numerix-timetracking.png)

and edit its entries
![Timeroll Entry](/wiki/img/numerix-timeroll-entry.png)


You can create invoices from tracked time and also adding manual entries along with different taxes, a generated invoice number etc. When done you can generate a PDF from it via the [Flying Saucer](https://github.com/flyingsaucerproject/flyingsaucer) project.

![Invoices](/wiki/img/numerix-invoice.png)

In the documents section a generated invoice automatically creates a new document or appends the new invoice version to an existing one:

![Documents](/wiki/img/numerix-attached-documents.png)

There's a calendar and you can add events:

![Calendar Event](/wiki/img/numerix-new-event.png)

And then there's the chat room of course. Any application nowadays must have chat, right :)
You can create multiple chat rooms, call out people in chat, see who's online, and receive notifications if you've been called:

![Chat Rooms](/wiki/img/numerix-chatroom.png)

![Chat](/wiki/img/numerix-chat.png)


Numerix was discontinued in 2017 due to lack of resources.
I post it here so maybe someone find some ideas in the code useful for his or her own projects.

# Building and Running Numerix

Numerix is a Leiningen based Clojure project.

# Twitter Bootstrap

Bootstrap v4.0 (some beta before 4) is included.

# MongoDB

This app uses MongoDB. Therefore, you will need to either have it installed locally, or use one of
the cloud providers and configure it in your props file. See config.MongoConfig for more info.

# Configuring

Please check `resources/props/default.props` and replace the placeholders with real values.

# Building

This app uses Leinigen 2.8. Start the repl inside the numerix directory and call the `run` function:

    bash$ lein repl
    > (run)

That will start the app and Figwheel will take care or automatically pushing any changes without reloading the page.

The server will be running at `http://localhost:10555`.




