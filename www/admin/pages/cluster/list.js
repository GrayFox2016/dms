var md = angular.module('module_cluster/list', ['base']);
md.controller('MainCtrl', function ($scope, $http, uiTips, uiValid) {
    $scope.ctrl = {};
    $scope.tmp = {};
    $scope.editOne = {globalEnvConf: {}};

    $scope.queryLl = function () {
        $http.get('/dms/cluster/list', {params: {}}).success(function (data) {
            $scope.ll = data;
        });
    };

    $scope.queryLl();

    $scope.edit = function (one) {
        $scope.editOne = _.clone(one);
        $scope.ctrl.isShowAdd = true;
    };

    $scope.save = function () {
        if (!uiValid.checkForm($scope.tmp.addForm) || !$scope.tmp.addForm.$valid) {
            uiTips.tips('Input Invalid');
            return;
        }

        var one = _.clone($scope.editOne);
        $http.post('/dms/cluster/update', one).success(function (data) {
            if (data.id) {
                $scope.ctrl.isShowAdd = false;
                $scope.queryLl();
            }
        });
    };

	$scope.delete = function(one){
		uiTips.confirm('Sure Delete - ' + one.name + '?', function(){
			$http.delete('/dms/cluster/delete/' + one.id).success(function(data){
				if(data.flag){
					var i = _.indexOf($scope.ll, one);
					$scope.ll.splice(i, 1);
				}
			});
		}, null);
	};   
});