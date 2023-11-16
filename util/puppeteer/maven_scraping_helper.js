const puppeteer = require('puppeteer-extra');
const StealthPlugin = require('puppeteer-extra-plugin-stealth');
const fs = require('fs');
const axios = require('axios');
const ProgressBar = require('progress');

puppeteer.use(StealthPlugin());


/**
 * Fetches all versions of a library from mvnrepository.com
 * @param page
 * @param library
 * @returns {Promise<*>}
 */
async function fetchLibraryVersions(page, library) {
    const url = `https://mvnrepository.com/artifact/${library.groupId}/${library.artifactId}`;
    await page.goto(url, {waitUntil: 'networkidle2'});

    return page.evaluate(() => {
        const versions = [];
        const rows = document.querySelectorAll('.grid.versions tbody tr');
        rows.forEach(row => {
            const version = row.querySelector('td a.vbtn')?.textContent;
            const vulnerabilities = row.querySelector('td a.vuln') ? parseInt(row.querySelector('td a.vuln').textContent.split(' ')[0]) : 0;
            const usages = row.querySelector('.pbt a') ? parseInt(row.querySelector('.pbt a').textContent.replace(/,/g, '')) : 0;
            const date = row.querySelector('.date')?.textContent;
            versions.push({version, vulnerabilities, usages, date});
        });
        return versions;
    });
}

/**
 * Extracts data about all the libraries from a search query results page on mvnrepository.com
 * @param page
 * @returns {Promise<*>}
 */
async function extractDataFromPage(page) {
    return page.evaluate(() => {
        function isAd(element) {
            return element.innerHTML.includes('adsbygoogle');
        }

        const data = [];
        document.querySelectorAll('.im').forEach(element => {
            if (!isAd(element)) {
                const description = element.querySelector('.im-description').innerText;
                const groupId = element.querySelector('.im-subtitle a').innerText;
                const artifactId = element.querySelector('.im-subtitle a:nth-child(2)').innerText;
                const usages = element.querySelector('.im-usage b') ? element.querySelector('.im-usage b').innerText.replace(/,/g, '') : '0';
                data.push({groupId, artifactId, description, usages});
            }
        });
        return data;
    });
}

function delay(time) {
    return new Promise(resolve => setTimeout(resolve, time));
}

/**
 * Fetches versions of all the libraries given in a list from mvnrepository.com
 * @returns {Promise<void>}
 */
async function mainGetVersions() {
    const browser = await puppeteer.launch({headless: false});
    const page = await browser.newPage();
    let libraries = JSON.parse(fs.readFileSync('all_uber_shaded_jars.json', 'utf8'));

    const bar = new ProgressBar('[:bar] :current/:total :percent :etas', {total: libraries.length});
    for (const library of libraries) {
        if (library.mostUsedVersion) { // already fetched
            continue;
        }
        const versions = await fetchLibraryVersions(page, library);

        if (versions.length > 0) {
            const mostUsedVersion = versions.reduce((a, b) => (a.usages > b.usages) ? a : b);
            const vulnerableVersions = versions.filter(v => v.vulnerabilities > 0);
            const mostUsedVulnerableVersion = vulnerableVersions.length > 0 ?
                vulnerableVersions.reduce((a, b) => (a.usages > b.usages) ? a : b) : null;

            library.mostUsedVersion = mostUsedVersion;
            library.mostUsedVulnerableVersion = mostUsedVulnerableVersion;
        }
        // wait for 2 to 3 seconds
        await delay(Math.floor(Math.random() * 1000) + 1000);
        bar.tick();
    }

    await browser.close();

    fs.writeFileSync('all_uber_shaded_jars_with_versions.json', JSON.stringify(libraries, null, 4));
}

/**
 * Fetches all the libraries from a search result from mvnrepository.com given a search query
 * @returns {Promise<void>}
 */
async function mainGetLibraries(searchQuery) {
    const browser = await puppeteer.launch({headless: false});
    const page = await browser.newPage();
    let currentPage = 1;
    const allData = [];
    while (true) {
        await page.goto(`https://mvnrepository.com/search?q=${searchQuery}&p=${currentPage}`, {waitUntil: 'networkidle2'});
        const data = await extractDataFromPage(page);
        allData.push(...data);

        const nextButton = await page.$('ul.search-nav li.current + li a');
        if (!nextButton) {
            break;
        }

        currentPage++;
        // wait for 2 to 3 seconds
        await delay(Math.floor(Math.random() * 1000) + 2000);
    }

    await browser.close();

    fs.writeFileSync(`${searchQuery}_jars.json`, JSON.stringify(allData, null, 4));
}

/**
 * Fetches all the JARs from the search.maven.org API given a search query
 * @param query
 * @param rowsPerPage
 * @returns {Promise<*[]>}
 */
async function fetchJars(query, rowsPerPage) {
    let start = 0;
    let hasMore = true;
    const allJars = [];
    while (hasMore) {
        const url = `https://search.maven.org/solrsearch/select?q=${query}&rows=${rowsPerPage}&start=${start}&wt=json`;
        const response = await axios.get(url);
        const docs = response.data.response.docs;

        const jars = docs
            .filter(doc => doc.p === 'jar')
            .map(doc => ({
                groupId: doc.g,
                artifactId: doc.a
            }));
        allJars.push(...jars);

        hasMore = start + rowsPerPage < response.data.response.numFound;
        start += rowsPerPage;
    }

    return allJars;
}

/**
 * Fetches all the JARs from the search.maven.org API given a search query and writes them to a file
 * @param query
 * @returns {Promise<void>}
 */
async function mainGetJars(query) {
    const jars = await fetchJars(query, 200);
    console.log(jars.length);
    fs.writeFileSync(`${query}_jars.json`, JSON.stringify(jars, null, 4));
}


async function main() {
    const args = process.argv.slice(2);
    if (args.length === 0) {
        console.log('Usage: node maven_scraping_helper.js <command>');
        console.log('Commands:');
        console.log('get-jars <query> - fetches all the JARs from the search.maven.org API given a search query');
        console.log('get-libraries - fetches all the libraries from a search result from mvnrepository.com given a search query');
        console.log('get-versions - fetches versions of all the libraries given in a list from mvnrepository.com');
        return;
    }

    const command = args[0];
    if (command === 'get-jars') {
        const query = args[1];
        await mainGetJars(query);
    } else if (command === 'get-libraries') {
        const searchQuery = args[1];
        await mainGetLibraries(searchQuery);
    } else if (command === 'get-versions') {
        await mainGetVersions();
    } else {
        console.log('Unknown command');
    }
}

main();