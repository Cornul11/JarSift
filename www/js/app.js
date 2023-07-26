angular
  .module("jar-website", [])
  .directive("fileModel", [
    "$parse",
    function ($parse) {
      return {
        restrict: "A",
        link: function (scope, element, attrs) {
          var model = $parse(attrs.fileModel);
          var modelSetter = model.assign;

          element.bind("change", function () {
            scope.$parent.$apply(function () {
              modelSetter(scope.$parent, element[0].files[0]);
            });
          });
        },
      };
    },
  ])
  .controller("mainController", function ($scope, $http, $location) {
    $scope.libraries = [];
    $scope.jarFile = null;

    $scope.clear = function () {
      $scope.libraries = [];
      $scope.jarFile = null;
    };
    $scope.analyze = function () {
      console.log($scope.jarFile);
      const fd = new FormData();
      fd.append("file", $scope.jarFile);
      $http
        .post("api/upload", fd, {
          transformRequest: angular.identity,
          headers: { "Content-Type": undefined },
        })
        .then(function (response) {
          $scope.libraries = response.data;
          console.log(response);
        });
    };
  });
