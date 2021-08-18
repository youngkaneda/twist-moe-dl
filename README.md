# twist-moe-dl

A simple Java script to download anime episodes from [twist.moe](https://twist.moe).

## Usage

```
git clone https://github.com/youngkaneda/twist-moe-dl
cd twist-moe-dl
mvn clean package
java -jar target/twist-moe-dl-1.0.jar berserk 1
```

If it finds more than one anime resource for the given name the following output will be presented.

```
Please select one of the following: 
1. Berserk (2016)
2. Berserk (2017)
3. Berserk Golden Arc Movies
4. .Berserk
> _ // put the selected index here
```

After picking the anime you want the download will start and output the current percentage.

```
Please select one of the following: 
1. Berserk (2016)
2. Berserk (2017)
3. Berserk Golden Arc Movies
4. .Berserk
> 3
downloading: 46%
```
---
Feel free to fork and contribute.
