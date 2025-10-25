# Old School Runescape Flip-Finder

This started off as a side project because I wanted an easy way to "flip" items
in runescape. Flipping is when you buy an item for a certain price and then sell
that item at hopefully a higher price than you bought it. This can be difficult
because the prices, supply and demand are all constantly changing and this affects
which items are good to flip. Flipping is a good way to earn passive income in a game
where you usually have to pay attention for hours to make significant profit.

## Features

This program is designed just to access data from the RSBuddy api and display it nicely
in a javafx table to view with certain constraints on supply, demand and profit. This is
a maven project written in java with an SQLite database. The server is a multi-threaded server
that allows clients to connect, disconnect and request updates on items that they double click
on.
