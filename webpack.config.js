var path = require('path');
module.exports = {
  entry: './web/index.js',
  output: {
    path: path.resolve(__dirname, 'target/web'),
    filename: 'bundle.js'
  },
  module: {
    loaders: [
      {test: /\.js$/, loaders: ['jsx', 'babel'], exclude: /node_modules/},
      {test: /\.css$/, loader: 'style!css'}
    ]
  }
};
