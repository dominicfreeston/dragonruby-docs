# Dragonruby API Docs

Site generator for alternative docs pages for the DragonRuby Game Toolkit API.

It's a single [babashka](https://babashka.org/) script, which uses [bootleg](https://github.com/retrogradeorbit/bootleg)'s support for [enlive](https://github.com/cgrand/enlive) to parse out then re-render the required content.

You can install the dependencies in a local temp directory and run the script in one step by running `build.sh`. It will throw an error if the top level structure of the source page has changed - `bb list-sections` can be used to obtain the current list and the script needs to be updated manually to match.

The source content is obtained from the [DragonRuby documentation](http://docs.dragonruby.org.s3-website-us-east-1.amazonaws.com/).

The output includes only what I consider to be the "core API docs", excluding the rationale, FAQs, getting started, deploying/publishing guides and code samples, making it easier to use a browser's search feature to find the relevant section. It splits out the TOC into a separate page - the reason for this is to make browsing the docs on a phone easier (swipe back to return to TOC). It also adds sticky headers which makes it easier to keep track of the current context.
