//! Product search: each request line is "<pattern>\t<catalogue line>", so
//! staff can try their own search patterns against the catalogue.
use regex::Regex;
use std::io::{BufRead, BufReader, Write};
use std::net::TcpListener;

pub fn matches(pattern: &str, text: &str) -> bool {
    Regex::new(pattern).map(|re| re.is_match(text)).unwrap_or(false)
}

fn main() -> std::io::Result<()> {
    let listener = TcpListener::bind("0.0.0.0:8083")?;
    for stream in listener.incoming() {
        let mut stream = stream?;
        let mut line = String::new();
        BufReader::new(&stream).read_line(&mut line)?;
        let (pattern, text) = line.trim_end().split_once('\t').unwrap_or(("", ""));
        writeln!(stream, "{}", matches(pattern, text))?;
    }
    Ok(())
}
