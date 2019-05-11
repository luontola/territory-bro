// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

const path = require('path');

module.exports = {
  entry: './web/js/index.js',
  output: {
    path: path.resolve(__dirname, 'target/webpack'),
    filename: 'bundle.js',
  },
  module: {
    rules: [
      {
        test: /\.js$/,
        exclude: path.resolve(__dirname, 'node_modules'),
        use: ['babel-loader'],
      },
      {
        // local scoped CSS
        test: /\.css$/,
        exclude: path.resolve(__dirname, 'node_modules'),
        use: [
          'style-loader',
          {loader: 'css-loader', options: {modules: true, localIdentName: '[name]__[local]--[hash:base64:5]'}},
        ],
      },
      {
        // global scoped CSS
        test: /\.css$/,
        include: path.resolve(__dirname, 'node_modules'),
        use: ['style-loader', 'css-loader'],
      },
    ]
  },
  devtool: 'source-map',
  devServer: {
    historyApiFallback: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        secure: false
      }
    }
  }
};
