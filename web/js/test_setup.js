// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

// Needed according to https://github.com/mochajs/mocha-examples/tree/master/packages/typescript-babel
const register = require('@babel/register').default;
register({extensions: ['.ts', '.tsx', '.js', '.jsx']});
